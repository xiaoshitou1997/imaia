/**
 * imaia WebUI API client — typed helpers over the loopback /api endpoints.
 * Dev: vite proxy forwards /api to 127.0.0.1:8091. Prod: same-origin static.
 */

export interface Overview {
  messagesToday: number
  aiRepliesToday: number
  tokensToday: number
  callsToday: number
  pendingActions: number
  recent: { threadId: string; senderName: string | null; direction: string; type: number; body: string; time: string }[]
}

export interface Contact {
  id: number
  wxid: string
  displayName: string
  kind: string
  persona: string | null
  remark: string | null
  muted: boolean | null
  lastSeenAt: string | null
}

export interface MessageRow {
  time: string
  direction: string
  senderId: string | null
  senderName: string | null
  type: number
  body: string
}

export interface Artifact {
  id: number
  threadId: string
  kind: string
  prompt: string | null
  modelId: string | null
  filename: string | null
  status: string
  createdAt: string
}

export interface ScheduledAction {
  id: number
  threadId: string
  action: string
  content: string | null
  prompt: string | null
  fireAt: string | null
  cron: string | null
  nextFireAt: string | null
  status: string
  createdBy: string
  lastFiredAt: string | null
}

export interface MemoryFact {
  id: number
  scope: string
  kind: string
  fact: string
  status: string
  supersedesId: number | null
  createdAt: string
}

export interface ModelCall {
  threadId: string | null
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

export interface ProviderModel {
  id: number
  providerId: number
  modelId: string
  name: string | null
  protocol: string
  contextWindow: number | null
  inputModalities: string[]
  outputModalities: string[]
  maxOutputTokens: number | null
  params: Record<string, string>
  enabled: boolean
}

export interface Provider {
  id: number
  name: string
  baseUrl: string
  apiKeyMasked: string
  enabled: boolean
  models: ProviderModel[]
}

export interface AssistantProfile {
  systemPrompt: string
  historyLimit: number
  replyThreads: string
  requireGroupMention: boolean
  ttsReply: boolean
  minMessageLength: number
  replyDelayMinMs: number
  replyDelayMaxMs: number
  enabled: boolean
  updatedAt: string
}

export interface RiskPolicy {
  repliesPerThreadPerMinute: number
  maxRepliesPerHour: number
  maxRepliesPerDay: number
  quietHoursStart: string
  quietHoursEnd: string
  updatedAt: string
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch('/api' + path, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  if (!res.ok) {
    let message = String(res.status)
    try {
      const body = await res.json()
      if (body && body.error) message = body.error
    } catch {
      /* non-json error body */
    }
    throw new Error(message)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

export const api = {
  overview: () => request<Overview>('/overview'),
  contacts: () => request<Contact[]>('/contacts'),
  updateContact: (id: number, body: Partial<Pick<Contact, 'persona' | 'remark' | 'muted'>>) =>
    request(`/contacts/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  messages: (wxid: string, limit = 200) =>
    request<MessageRow[]>(`/threads/${encodeURIComponent(wxid)}/messages?limit=${limit}`),
  artifacts: () => request<Artifact[]>('/artifacts'),
  actions: () => request<ScheduledAction[]>('/actions'),
  createAction: (body: {
    threadId: string
    action: string
    content?: string | null
    prompt?: string | null
    fireAt?: string | null
    cron?: string | null
  }) => request<{ id: number }>('/actions', { method: 'POST', body: JSON.stringify(body) }),
  cancelAction: (id: number) => request(`/actions/${id}/cancel`, { method: 'POST' }),
  fireAction: (id: number) => request(`/actions/${id}/fire`, { method: 'POST' }),
  memories: () => request<MemoryFact[]>('/memories'),
  updateMemory: (id: number, fact: string) =>
    request(`/memories/${id}`, { method: 'PUT', body: JSON.stringify({ fact }) }),
  deleteMemory: (id: number) => request(`/memories/${id}`, { method: 'DELETE' }),
  promoteMemory: (id: number) => request(`/memories/${id}/promote`, { method: 'POST' }),
  calls: () => request<ModelCall[]>('/calls'),
  providers: () => request<Provider[]>('/providers'),
  createProvider: (body: { name: string; baseUrl: string; apiKey?: string }) =>
    request<{ id: number }>('/providers', { method: 'POST', body: JSON.stringify(body) }),
  updateProvider: (id: number, body: { baseUrl?: string; apiKey?: string; enabled?: boolean }) =>
    request(`/providers/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteProvider: (id: number) => request(`/providers/${id}`, { method: 'DELETE' }),
  deleteModel: (id: number) => request(`/models/${id}`, { method: 'DELETE' }),
  profile: () => request<AssistantProfile>('/settings/profile'),
  updateProfile: (body: Partial<AssistantProfile>) =>
    request('/settings/profile', { method: 'PUT', body: JSON.stringify(body) }),
  risk: () => request<RiskPolicy>('/settings/risk'),
  updateRisk: (body: Partial<RiskPolicy>) =>
    request('/settings/risk', { method: 'PUT', body: JSON.stringify(body) }),
  runtime: () => request<Record<string, string>>('/settings/runtime'),
  runAnalysis: (contactId: number) =>
    request<{ profile: string }>(`/analysis/${contactId}`, { method: 'POST' }),
  getAnalysis: (contactId: number) => request<{ profile: string }>(`/analysis/${contactId}`),
  sendText: (threadId: string, text: string) =>
    request<{ sendId: string }>('/imaia/send', {
      method: 'POST',
      body: JSON.stringify({ threadId, text }),
    }).catch(() => {
      // 老控制端点在 /imaia 前缀下（非 /api）——直接同源调用
      return fetch('/imaia/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ threadId, text }),
      }).then((r) => r.json())
    }),
  ask: (question: string) =>
    fetch('/imaia/ask', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question }),
    }).then((r) => r.json()),
}
