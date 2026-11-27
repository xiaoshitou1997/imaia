/**
 * imaia 控制台 API 客户端。
 * 后端统一包络 { code, message, data }；code 非 0 或 HTTP 非 2xx 一律抛 ApiError。
 * 全局副作用（401 跳登录、mutation toast）挂在 main.tsx 的 QueryClient 上。
 */

export class ApiError extends Error {
  status: number
  code: number

  constructor(message: string, status: number, code: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

interface Envelope<T> {
  code: number
  message: string
  data: T | null
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
    // 非 JSON 响应体（网关/代理层错误页）
  }

  if (!res.ok || !body || typeof body.code !== 'number' || body.code !== 0) {
    const message =
      body && typeof body.message === 'string' && body.message
        ? body.message
        : '请求失败（HTTP ' + res.status + '）'
    throw new ApiError(message, res.status, body?.code ?? res.status)
  }
  return body.data as T
}

export interface Paged<T> {
  items: T[]
  page: number
  size: number
  total: number
  totalPages: number
}

export interface Overview {
  accounts: number
  conversations: number
  messagesTotal: number
  messagesToday: number
  processedToday: number
  pendingTasks: number
  assistantEnabled: boolean
}

export interface PlatformState {
  platformCode: string
  configured: boolean
}

export interface RuntimeInfo {
  chat: boolean
  images: boolean
  video: boolean
  search: boolean
  platforms: PlatformState[]
}

export interface RecentMessage {
  id: number
  conversationLabel: string
  sender: string
  own: boolean
  body: string
  kind: string
  occurredAt: string
}

export interface Account {
  id: number
  platformCode: string
  name: string
  enabled: boolean
  /** JSON 文本；密钥字段为掩码，保留掩码提交即不改原值。 */
  config: string
  updatedAt: string
}

export interface Conversation {
  id: number
  /** 平台原生会话 id（wxid / xxx@chatroom / oc_xxx）。排障用，不要展示给用户。 */
  platformId: string
  /** 用户可见的会话名，由后端给出：title 优先，否则按封闭值域 code 给占位名。 */
  displayName: string
  title: string | null
  remark: string | null
  kind: string
  persona: string | null
  autoreply: boolean
  selfCommandAlias: string | null
  selfCommandEnabled: boolean
  lastSeenAt: string | null
  lastMessageAt: string | null
  accountId: number
  platformCode: string | null
  connectionName: string | null
  messageCount: number
}

export interface MessageAttachment {
  id: number
  kind: string
  filename: string | null
  mimeType: string | null
  sizeBytes: number | null
  durationMs: number | null
  status: string
}

export interface MessageRow {
  id: number
  platformId: string
  contactId: number | null
  senderName: string | null
  direction: string
  kind: string
  mentioned: boolean
  origin: string
  body: string | null
  occurredAt: string
  archivedAt: string
  attachments: MessageAttachment[]
}

export interface ContactBindRow {
  id: number
  contactId: number
  platformId: string | null
  displayName: string | null
  role: string
  lastSeenAt: string | null
  analyzed: boolean
}

export interface AnalysisRow {
  id: number
  contactId: number
  contactName: string | null
  profile: string
  messageCount: number | null
  generatedAt: string
}

export interface SearchHit {
  id: number
  conversationId: number
  conversationLabel: string
  contactId: number | null
  senderName: string | null
  kind: string
  body: string | null
  archivedAt: string
}

export interface TaskRow {
  id: number
  conversationId: number
  conversationLabel: string | null
  content: string | null
  prompt: string | null
  runAt: string | null
  cronExpr: string | null
  timezone: string
  nextRunAt: string | null
  status: string
  createdBy: string
  lastRunAt: string | null
  createdAt: string
}

export interface TaskRun {
  id: number
  scheduledFor: string | null
  startedAt: string | null
  finishedAt: string | null
  status: string
  attempt: number
  outputText: string | null
  outboundLedgerId: number | null
  error: string | null
}

export interface ModelRow {
  id: number
  modelKey: string
  name: string | null
  description: string | null
  modalitiesIn: string
  modalitiesOut: string
  supportsVision: boolean
  supportsReasoning: boolean
  supportsToolCalls: boolean
  limitContext: number | null
  limitOutput: number | null
  costInput: number | null
  costOutput: number | null
  params: string
  available: boolean
  enabled: boolean
  source: string
  syncedAt: string | null
}

export interface Provider {
  id: number
  name: string
  baseUrl: string
  apiKey: string
  enabled: boolean
  modelsDevId: string | null
  models: ModelRow[]
  /** 最近一次同步的人读说明；null 表示尚未同步。 */
  sync: string | null
}

export interface SyncResult {
  listed: number
  added: number
  updated: number
  retired: number
}

export interface SyncHistoryResult {
  scanned: number
  added: number
  pages: number
  failed: number
  hasMore: boolean
}

export interface Settings {
  systemPrompt: string
  historyLimit: number
  requireGroupMention: boolean
  quietHoursEnabled: boolean
  quietHoursStart: string
  quietHoursEnd: string
  timezone: string
  enabled: boolean
  selfCommandAlias: string
  selfCommandEnabled: boolean
  webSearchBaseUrl: string
  webSearchApiKey: string
  imageBaseUrl: string
  imageApiKey: string
  imageModel: string
  videoBaseUrl: string
  videoApiKey: string
  videoModel: string
}

export const attachmentContentUrl = (conversationId: number, attachmentId: number) =>
  '/api/conversations/' + conversationId + '/attachments/' + attachmentId + '/content'

export const api = {
  // ---- 登录
  login: (body: { username: string; password: string }) =>
    request<{ username: string }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  // ---- 概览
  overview: () => request<Overview>('/overview'),
  recentMessages: () => request<RecentMessage[]>('/messages/recent'),
  runtime: () => request<RuntimeInfo>('/runtime'),

  // ---- 平台账号
  accounts: () => request<Account[]>('/accounts'),
  createAccount: (body: {
    platformCode: string
    name: string
    enabled?: boolean
    config?: unknown
  }) =>
    request<Account>('/accounts', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updateAccount: (
    id: number,
    body: { name?: string; enabled?: boolean; config?: unknown }
  ) =>
    request<Account>('/accounts/' + id, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  deleteAccount: (id: number) =>
    request<string>('/accounts/' + id, { method: 'DELETE' }),

  // ---- 会话与消息
  conversations: (query?: string, page = 1, size = 20) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (query) params.set('query', query)
    return request<Paged<Conversation>>('/conversations?' + params.toString())
  },
  updateConversation: (
    id: number,
    body: {
      title?: string
      remark?: string
      persona?: string
      autoreply?: boolean
      selfCommandAlias?: string
      selfCommandEnabled?: boolean
    }
  ) =>
    request<number>('/conversations/' + id, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  conversationMessages: (id: number, keyword?: string) =>
    request<MessageRow[]>(
      '/conversations/' + id + '/messages' +
        (keyword ? '?keyword=' + encodeURIComponent(keyword) : '')
    ),
  syncHistory: (id: number, pages = 5, limit = 100) =>
    request<SyncHistoryResult>(
      '/conversations/' + id + '/sync-history?pages=' + pages + '&limit=' + limit,
      { method: 'POST' }
    ),
  conversationContacts: (id: number) =>
    request<ContactBindRow[]>('/conversations/' + id + '/contacts'),
  runAnalysis: (id: number, contactId: number) =>
    request<AnalysisRow>('/conversations/' + id + '/analysis', {
      method: 'POST',
      body: JSON.stringify({ contactId }),
    }),
  conversationAnalysis: (id: number) =>
    request<AnalysisRow[]>('/conversations/' + id + '/analysis'),
  searchMessages: (keyword: string) =>
    request<SearchHit[]>(
      '/conversations/search?keyword=' + encodeURIComponent(keyword)
    ),

  // ---- 定时任务
  tasks: (page = 1, size = 20, status?: string) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (status) params.set('status', status)
    return request<Paged<TaskRow>>('/tasks?' + params.toString())
  },
  createTask: (body: {
    conversationId: number
    content?: string | null
    prompt?: string | null
    fireAt?: string | null
    cron?: string | null
    timezone?: string
  }) =>
    request<number>('/tasks', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  cancelTask: (id: number) =>
    request<string>('/tasks/' + id + '/cancel', { method: 'POST' }),
  fireTask: (id: number) =>
    request<string>('/tasks/' + id + '/fire', { method: 'POST' }),
  taskRuns: (id: number, page = 1, size = 10) =>
    request<Paged<TaskRun>>(
      '/tasks/' + id + '/runs?page=' + page + '&size=' + size
    ),

  // ---- 供应商与模型
  providers: () => request<Provider[]>('/ai-providers'),
  createProvider: (body: {
    name: string
    baseUrl: string
    apiKey?: string
    enabled?: boolean
    modelsDevId?: string
  }) =>
    request<{ provider: Provider; sync: SyncResult }>('/ai-providers', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updateProvider: (
    id: number,
    body: {
      baseUrl?: string
      apiKey?: string
      enabled?: boolean
      modelsDevId?: string | null
    }
  ) =>
    request<Provider>('/ai-providers/' + id, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  deleteProvider: (id: number) =>
    request<string>('/ai-providers/' + id, { method: 'DELETE' }),
  syncModels: (id: number) =>
    request<SyncResult>('/ai-providers/' + id + '/sync-models', {
      method: 'POST',
    }),
  updateModel: (id: number, body: { enabled?: boolean; name?: string }) =>
    request<ModelRow>('/ai-providers/ai-models/' + id, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),

  // ---- 设置
  getSettings: () => request<Settings>('/settings'),
  updateSettings: (body: Partial<Settings>) =>
    request<Settings>('/settings', {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
}
