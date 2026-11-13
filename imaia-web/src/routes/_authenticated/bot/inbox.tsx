import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api, type Conversation, type MemberRow } from '@/features/imaia/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'

export const Route = createFileRoute('/_authenticated/bot/inbox')({
  component: InboxPage,
})

const fmt = (t: string | null | undefined) => (t ? new Date(t).toLocaleString('zh-CN', { hour12: false }) : '—')
const cut = (s: string | null | undefined, n = 80) => (s ? (s.length > n ? s.slice(0, n) + '…' : s) : '—')

function label(c: Conversation) {
  return c.displayName || c.externalId
}

function InboxPage() {
  const qc = useQueryClient()
  const [query, setQuery] = useState('')
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [keyword, setKeyword] = useState('')
  const [tab, setTab] = useState('messages')
  const [personaDraft, setPersonaDraft] = useState('')

  const conversations = useQuery({
    queryKey: ['conversations', query],
    queryFn: () => api.conversations(query || undefined),
  })
  const selected = (conversations.data || []).find((c) => c.id === selectedId) ?? null

  const messages = useQuery({
    queryKey: ['messages', selectedId, keyword],
    queryFn: () => api.conversationMessages(selectedId!, keyword || undefined),
    enabled: selectedId !== null && tab === 'messages',
  })
  const aiMessages = useQuery({
    queryKey: ['ai-messages', selectedId],
    queryFn: () => api.conversationAiMessages(selectedId!),
    enabled: selectedId !== null && tab === 'ai',
  })
  const members = useQuery({
    queryKey: ['members', selectedId],
    queryFn: () => api.conversationMembers(selectedId!),
    enabled: selectedId !== null && tab === 'members',
  })
  const analyses = useQuery({
    queryKey: ['analysis', selectedId],
    queryFn: () => api.conversationAnalysis(selectedId!),
    enabled: selectedId !== null && tab === 'members',
  })

  const selectConversation = (c: Conversation) => {
    setSelectedId(c.id)
    setPersonaDraft(c.persona ?? '')
  }

  const toggleAutoreply = (c: Conversation, on: boolean) => {
    api.updateConversation(c.id, { autoreply: on })
      .then(() => {
        toast.success(on ? '已开启本线自动回复' : '已关闭本线自动回复')
        qc.invalidateQueries({ queryKey: ['conversations'] })
      })
      .catch((e: Error) => toast.error(e.message))
  }

  const savePersona = () => {
    if (!selected) return
    api.updateConversation(selected.id, { persona: personaDraft })
      .then(() => toast.success('人设已保存，下一轮回复即生效'))
      .catch((e: Error) => toast.error(e.message))
  }

  const generateProfile = (member: MemberRow) => {
    if (!selected) return
    api.runAnalysis(selected.id, member.memberExternalId, member.displayName ?? undefined)
      .then(() => {
        toast.success('画像已生成')
        qc.invalidateQueries({ queryKey: ['members', selected.id] })
        qc.invalidateQueries({ queryKey: ['analysis', selected.id] })
      })
      .catch((e: Error) => toast.error(e.message))
  }

  return (
    <div className='space-y-4'>
      <Input
        placeholder='搜索会话（显示名 / 平台会话 ID）'
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />
      <div className='grid grid-cols-1 gap-4 xl:grid-cols-5'>
        <Card className='xl:col-span-2'>
          <CardHeader className='pb-2'>
            <CardTitle className='text-sm'>统一收件箱（{conversations.data?.length ?? 0}）</CardTitle>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>会话</TableHead>
                  <TableHead>平台</TableHead>
                  <TableHead>消息</TableHead>
                  <TableHead>自动回复</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(conversations.data || []).map((c) => (
                  <TableRow
                    key={c.id}
                    className={c.id === selectedId ? 'bg-accent cursor-pointer' : 'cursor-pointer'}
                    onClick={() => selectConversation(c)}
                  >
                    <TableCell>
                      <div className='text-sm'>{label(c)}</div>
                      <div className='text-muted-foreground text-xs'>{c.externalId}</div>
                    </TableCell>
                    <TableCell>
                      <Badge variant={c.kind === 'group' ? 'default' : 'secondary'}>
                        {c.platform} · {c.kind === 'group' ? '群' : '私聊'}
                      </Badge>
                    </TableCell>
                    <TableCell className='text-muted-foreground text-xs'>{c.messageCount}</TableCell>
                    <TableCell onClick={(e) => e.stopPropagation()}>
                      <Switch checked={c.autoreply} onCheckedChange={(on) => toggleAutoreply(c, on)} />
                    </TableCell>
                  </TableRow>
                ))}
                {(conversations.data || []).length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} className='text-muted-foreground text-center text-xs'>
                      暂无会话；消息到达后自动登记
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </CardContent>
        </Card>

        <Card className='xl:col-span-3'>
          {!selected ? (
            <CardContent className='text-muted-foreground flex min-h-64 items-center justify-center text-sm'>
              ← 从左侧选择一个会话
            </CardContent>
          ) : (
            <>
              <CardHeader className='pb-2'>
                <CardTitle className='text-sm'>{label(selected)}</CardTitle>
                <div className='text-muted-foreground text-xs'>
                  {selected.platform} · {selected.externalId} · 最近活跃 {fmt(selected.lastSeenAt)}
                </div>
              </CardHeader>
              <CardContent className='space-y-4'>
                <div className='space-y-2'>
                  <div className='text-xs font-medium'>本线人设（空 = 全局默认提示词，保存后下一轮生效）</div>
                  <div className='flex gap-2'>
                    <Input
                      placeholder='如：毒舌群友，说话简短带梗'
                      value={personaDraft}
                      onChange={(e) => setPersonaDraft(e.target.value)}
                    />
                    <Button variant='secondary' onClick={savePersona}>保存</Button>
                  </div>
                </div>

                <Tabs value={tab} onValueChange={setTab}>
                  <TabsList>
                    <TabsTrigger value='messages'>归档消息</TabsTrigger>
                    <TabsTrigger value='ai'>AI 上下文</TabsTrigger>
                    <TabsTrigger value='members'>成员与画像</TabsTrigger>
                  </TabsList>

                  <TabsContent value='messages' className='space-y-3'>
                    <Input
                      placeholder='在本会话内按关键词过滤'
                      value={keyword}
                      onChange={(e) => setKeyword(e.target.value)}
                    />
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>时间</TableHead>
                          <TableHead>发送方</TableHead>
                          <TableHead>类型</TableHead>
                          <TableHead>内容</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {(messages.data || []).map((m) => (
                          <TableRow key={m.id}>
                            <TableCell className='text-muted-foreground text-xs'>{fmt(m.archivedAt)}</TableCell>
                            <TableCell className='text-xs'>
                              <Badge variant={m.origin === 'ai' ? 'default' : m.direction === 'outgoing' ? 'outline' : 'secondary'}>
                                {m.origin === 'ai' ? 'AI' : (m.senderName || m.senderId || '对方')}
                              </Badge>
                            </TableCell>
                            <TableCell className='text-muted-foreground text-xs'>
                              {m.category}{m.mentioned ? ' @' : ''}
                            </TableCell>
                            <TableCell className='max-w-md text-xs'>{cut(m.body, 200)}</TableCell>
                          </TableRow>
                        ))}
                        {(messages.data || []).length === 0 && (
                          <TableRow>
                            <TableCell colSpan={4} className='text-muted-foreground text-center text-xs'>无消息</TableCell>
                          </TableRow>
                        )}
                      </TableBody>
                    </Table>
                  </TabsContent>

                  <TabsContent value='ai'>
                    <div className='space-y-2'>
                      {(aiMessages.data || []).slice().reverse().map((m) => (
                        <div key={m.id} className='rounded-md border p-2'>
                          <div className='text-muted-foreground text-xs'>
                            {m.role}{m.toolName ? ' · ' + m.toolName : ''} · {fmt(m.createdAt)}
                          </div>
                          <div className='whitespace-pre-wrap text-sm'>{cut(m.content, 500)}</div>
                        </div>
                      ))}
                      {(aiMessages.data || []).length === 0 && (
                        <div className='text-muted-foreground text-sm'>暂无上下文（自动回复开启后产生）</div>
                      )}
                    </div>
                  </TabsContent>

                  <TabsContent value='members' className='space-y-3'>
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>成员</TableHead>
                          <TableHead>ID</TableHead>
                          <TableHead>最近活跃</TableHead>
                          <TableHead>画像</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {(members.data || []).map((m) => (
                          <TableRow key={m.id}>
                            <TableCell className='text-sm'>{m.displayName || '（未知）'}</TableCell>
                            <TableCell className='text-muted-foreground text-xs'>{m.memberExternalId}</TableCell>
                            <TableCell className='text-muted-foreground text-xs'>{fmt(m.lastSeenAt)}</TableCell>
                            <TableCell>
                              <Button size='sm' variant='secondary' onClick={() => generateProfile(m)}>
                                {m.analyzed ? '重新生成' : '生成画像'}
                              </Button>
                            </TableCell>
                          </TableRow>
                        ))}
                        {(members.data || []).length === 0 && (
                          <TableRow>
                            <TableCell colSpan={4} className='text-muted-foreground text-center text-xs'>
                              群消息到达后自动登记成员
                            </TableCell>
                          </TableRow>
                        )}
                      </TableBody>
                    </Table>
                    {(analyses.data || []).length > 0 && (
                      <div className='space-y-2'>
                        <div className='text-xs font-medium'>已生成的画像</div>
                        {(analyses.data || []).map((a) => (
                          <div key={a.id} className='rounded-md border p-2'>
                            <div className='text-muted-foreground text-xs'>
                              {a.memberName || a.memberExternalId} · {a.messageCount ?? 0} 条消息 · {fmt(a.generatedAt)}
                            </div>
                            <div className='whitespace-pre-wrap text-sm'>{a.profile}</div>
                          </div>
                        ))}
                      </div>
                    )}
                  </TabsContent>
                </Tabs>
              </CardContent>
            </>
          )}
        </Card>
      </div>
    </div>
  )
}
