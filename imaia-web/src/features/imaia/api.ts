/**
 * imaia WebUI API client — typed helpers over the loopback /api endpoints (v0.2).
 * Every response uses the { code, message, data } envelope; this client unwraps it
 * and surfaces non-zero codes as thrown errors (toast in pages).
 * Dev: vite proxy forwards /api to 127.0.0.1:8091. Prod: same-origin static.
 */

export interface Overview {
  connections: number
  conversations: number
  messagesTotal: number
  messagesToday: number
  processedToday: number
  pendingTasks: number
  memories: number
  artifacts: number
  calls: number
  reports: number
  assistantEnabled: boolean
}

export interface Connection {
  id: number
  platform: string
  name: string
  enabled: boolean
  /** JSON text with secret values masked; submitting the mask restores the stored value. */
  config: string
  updatedAt: string
}

export interface Conversation {
  id: number
  externalId: string
  displayName: string | null
  kind: string
  persona: string | null
  autoreply: boolean
  lastSeenAt: string | null
  connectionId: number
  platform: string | null
  connectionName: string | null
  messageCount: number
}

export interface MessageRow {
  id: number
  messageId: string
  senderId: string | null
  senderName: string | null
  direction: string
  category: string
  mentioned: boolean
  origin: string
  body: string | null
  archivedAt: string
}

export interface AiMessageRow {
  id: number
  role: string
  content: string
  toolName: string | null
  createdAt: string
}

export interface MemberRow {
  id: number
  memberExternalId: string
  displayName: string | null
  lastSeenAt: string | null
  analyzed: boolean
}

export interface AnalysisRow {
  id: number
  memberExternalId: string
  memberName: string | null
  profile: string
  messageCount: number | null
  generatedAt: string
}

export interface TaskRow {
  id: number
  conversationId: number
  conversationLabel: string
  kind: string
  content: string | null
  prompt: string | null
  fireAt: string | null
  cron: string | null
  nextFireAt: string | null
  status: string
  createdBy: string
  lastFiredAt: string | null
  lastError: string | null
  createdAt: string
}

export interface TaskCreate {
  conversationId: number
  kind: string
  content?: string | null
  prompt?: string | null
  fireAt?: string | null
  cron?: string | null
  params?: Record<string, string>
}

export interface MemoryRow {
  id: number
  conversationId: number
  conversationLabel: string
  fact: string
  createdAt: string
}

export interface ReportRow {
  id: number
  conversationId: number
  conversationLabel: string
  periodType: string
  periodStart: string
  aiReport: string | null
  createdAt: string
}

export interface Artifact {
  id: number
  conversationId: number
  kind: string
  prompt: string | null
  filename: string | null
  status: string
  createdAt: string
}

export interface CallRow {
  id: number
  purpose: string
  providerName: string | null
  modelId: string | null
  promptTokens: number | null
  completionTokens: number | null
  durationMs: number | null
  ok: boolean
  error: string | null
  createdAt: string
}

export interface ModelRow {
  id: number
  modelId: string
  name: string | null
  protocol: string
  contextWindow: number | null
  inputModalities: string
  outputModalities: string
  maxOutputTokens: number | null
  params: string
  enabled: boolean
}

export interface Provider {
  id: number
  name: string
  baseUrl: string
  apiKey: string
  enabled: boolean
  models: ModelRow[]
}

export interface Settings {
  systemPrompt: string
  historyLimit: number
  requireGroupMention: boolean
  quietHoursEnabled: boolean
  quietHoursStart: string
  quietHoursEnd: string
  enabled: boolean
}

interface Envelope<T> {
  code: number
  message: string
  data: T
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch('/api' + path, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  let body: Envelope<T> | null = null
  try {
    body = (await res.json()) as Envelope<T>
  } catch {
    /* non-json error body */
  }
  if (!res.ok || !body || typeof body.code !== 'number' || body.code !== 0) {
    throw new Error(body?.message || 'HTTP ' + res.status)
  }
  return body.data
}

export const api = {
  overview: () => request<Overview>('/overview'),

  // ---- 接入连接
  connections: () => request<Connection[]>('/connections'),
  createConnection: (body: { platform: string; name: string; enabled?: boolean; config?: unknown }) =>
    request<Connection>('/connections', { method: 'POST', body: JSON.stringify(body) }),
  updateConnection: (id: number, body: { name?: string; enabled?: boolean; config?: unknown }) =>
    request<Connection>(`/connections/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteConnection: (id: number) => request<string>(`/connections/${id}`, { method: 'DELETE' }),

  // ---- 会话与消息
  conversations: (query?: string) =>
    request<Conversation[]>('/conversations' + (query ? '?query=' + encodeURIComponent(query) : '')),
  updateConversation: (id: number, body: {
    displayName?: string
    remark?: string
    persona?: string
    autoreply?: boolean
  }) => request<number>(`/conversations/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  conversationMessages: (id: number, keyword?: string) =>
    request<MessageRow[]>(
      `/conversations/${id}/messages` + (keyword ? '?keyword=' + encodeURIComponent(keyword) : '')),
  conversationAiMessages: (id: number) => request<AiMessageRow[]>(`/conversations/${id}/ai-messages`),
  searchMessages: (keyword: string) =>
    request<Record<string, unknown>[]>('/messages/search?keyword=' + encodeURIComponent(keyword)),
  conversationMembers: (id: number) => request<MemberRow[]>(`/conversations/${id}/members`),
  runAnalysis: (id: number, memberExternalId: string, memberName?: string) =>
    request<AnalysisRow>(`/conversations/${id}/analysis`, {
      method: 'POST',
      body: JSON.stringify({ memberExternalId, memberName }),
    }),
  conversationAnalysis: (id: number) => request<AnalysisRow[]>(`/conversations/${id}/analysis`),

  // ---- 定时任务
  tasks: () => request<TaskRow[]>('/tasks'),
  createTask: (body: TaskCreate) => request<number>('/tasks', { method: 'POST', body: JSON.stringify(body) }),
  cancelTask: (id: number) => request<string>(`/tasks/${id}/cancel`, { method: 'POST' }),
  fireTask: (id: number) => request<string>(`/tasks/${id}/fire`, { method: 'POST' }),

  // ---- 记忆
  memories: () => request<MemoryRow[]>('/memories'),
  updateMemory: (id: number, fact: string) =>
    request<string>(`/memories/${id}`, { method: 'PUT', body: JSON.stringify({ fact }) }),
  deleteMemory: (id: number) => request<string>(`/memories/${id}`, { method: 'DELETE' }),

  // ---- 洞察报表
  reports: (conversationId?: number) =>
    request<ReportRow[]>('/reports' + (conversationId ? '?conversationId=' + conversationId : '')),
  createReport: (body: { conversationId: number; periodType: string }) =>
    request<ReportRow>('/reports', { method: 'POST', body: JSON.stringify(body) }),

  // ---- 产物与调用
  artifacts: () => request<Artifact[]>('/artifacts'),
  calls: () => request<CallRow[]>('/calls'),

  // ---- 模型目录
  providers: () => request<Provider[]>('/providers'),
  createProvider: (body: { name: string; baseUrl: string; apiKey?: string; enabled?: boolean }) =>
    request<Provider>('/providers', { method: 'POST', body: JSON.stringify(body) }),
  updateProvider: (id: number, body: { baseUrl?: string; apiKey?: string; enabled?: boolean }) =>
    request<Provider>(`/providers/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteProvider: (id: number) => request<string>(`/providers/${id}`, { method: 'DELETE' }),
  createModel: (providerId: number, body: {
    modelId: string
    name?: string
    protocol: string
    contextWindow?: number | null
    inputModalities?: string[]
    outputModalities?: string[]
    maxOutputTokens?: number | null
    params?: Record<string, string>
    enabled?: boolean
  }) => request<ModelRow>(`/providers/${providerId}/models`, { method: 'POST', body: JSON.stringify(body) }),
  deleteModel: (id: number) => request<string>(`/models/${id}`, { method: 'DELETE' }),

  // ---- 设置与运行时
  getSettings: () => request<Settings>('/settings'),
  updateSettings: (body: Partial<Settings>) =>
    request<Settings>('/settings', { method: 'PUT', body: JSON.stringify(body) }),
  runtime: () => request<Record<string, unknown>>('/runtime'),
}
