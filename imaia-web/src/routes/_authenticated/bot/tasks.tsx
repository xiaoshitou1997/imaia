import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api } from '@/features/imaia/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'

export const Route = createFileRoute('/_authenticated/bot/tasks')({
  component: TasksPage,
})

const fmt = (t: string | null | undefined) => (t ? new Date(t).toLocaleString('zh-CN', { hour12: false }) : '—')
const cut = (s: string | null | undefined, n = 60) => (s ? (s.length > n ? s.slice(0, n) + '…' : s) : '—')

const KIND_LABEL: Record<string, string> = {
  reminder: '一次性提醒',
  cron: '周期任务',
  report: '周期报告',
}

function TasksPage() {
  const qc = useQueryClient()
  const tasks = useQuery({ queryKey: ['tasks'], queryFn: api.tasks })
  const conversations = useQuery({ queryKey: ['conversations', ''], queryFn: () => api.conversations() })

  const [conversationId, setConversationId] = useState('')
  const [kind, setKind] = useState('reminder')
  const [content, setContent] = useState('')
  const [prompt, setPrompt] = useState('')
  const [fireAt, setFireAt] = useState('')
  const [cron, setCron] = useState('')

  const invalidate = () => qc.invalidateQueries({ queryKey: ['tasks'] })

  const create = () => {
    if (!conversationId) {
      toast.error('请选择目标会话')
      return
    }
    api.createTask({
      conversationId: Number(conversationId),
      kind,
      content: content || null,
      prompt: kind === 'cron' ? (prompt || null) : null,
      fireAt: kind === 'reminder' && fireAt ? fireAt.replace('T', ' ') : null,
      cron: kind !== 'reminder' ? (cron || null) : null,
    })
      .then(() => {
        toast.success('任务已创建')
        setContent(''); setPrompt(''); setFireAt(''); setCron('')
        invalidate()
      })
      .catch((e: Error) => toast.error(e.message))
  }

  const cancel = (id: number) =>
    api.cancelTask(id).then(() => { toast.success('已取消'); invalidate() }).catch((e: Error) => toast.error(e.message))

  const fire = (id: number) =>
    api.fireTask(id).then(() => { toast.success('已触发，30 秒内执行'); invalidate() }).catch((e: Error) => toast.error(e.message))

  return (
    <div className='space-y-4'>
      <Card>
        <CardHeader className='pb-2'>
          <CardTitle className='text-sm'>新建任务（任务执行不受静默时段限制——是你主动要求的）</CardTitle>
        </CardHeader>
        <CardContent className='space-y-3'>
          <div className='grid grid-cols-2 gap-4'>
            <select
              className='border-input bg-background rounded-md border px-3 py-2 text-sm'
              value={conversationId}
              onChange={(e) => setConversationId(e.target.value)}
            >
              <option value=''>选择目标会话…</option>
              {(conversations.data || []).map((c) => (
                <option key={c.id} value={c.id}>
                  {c.platform} · {c.displayName || c.externalId}
                </option>
              ))}
            </select>
            <select
              className='border-input bg-background rounded-md border px-3 py-2 text-sm'
              value={kind}
              onChange={(e) => setKind(e.target.value)}
            >
              <option value='reminder'>一次性提醒</option>
              <option value='cron'>周期任务（固定文本 / AI 措辞）</option>
              <option value='report'>周期报告</option>
            </select>
            {kind === 'reminder' && (
              <>
                <Input
                  type='datetime-local'
                  value={fireAt}
                  onChange={(e) => setFireAt(e.target.value)}
                />
                <Input placeholder='提醒内容' value={content} onChange={(e) => setContent(e.target.value)} />
              </>
            )}
            {kind === 'cron' && (
              <>
                <Input placeholder='cron 表达式，如 0 8 * * *' value={cron} onChange={(e) => setCron(e.target.value)} />
                <Input placeholder='固定文本（可选）' value={content} onChange={(e) => setContent(e.target.value)} />
                <Input
                  placeholder='AI 措辞指令（可选，设置后由 AI 生成消息）'
                  className='col-span-2'
                  value={prompt}
                  onChange={(e) => setPrompt(e.target.value)}
                />
              </>
            )}
            {kind === 'report' && (
              <Input
                placeholder='cron 表达式，如 0 9 * * 1（每周一）'
                value={cron}
                onChange={(e) => setCron(e.target.value)}
              />
            )}
          </div>
          <Button onClick={create}>创建</Button>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>目标会话</TableHead>
                <TableHead>类型</TableHead>
                <TableHead>内容/指令</TableHead>
                <TableHead>时间</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>来源</TableHead>
                <TableHead>操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(tasks.data || []).map((t) => (
                <TableRow key={t.id}>
                  <TableCell className='text-xs'>{t.conversationLabel}</TableCell>
                  <TableCell><Badge variant='outline'>{KIND_LABEL[t.kind] ?? t.kind}</Badge></TableCell>
                  <TableCell className='text-muted-foreground max-w-xs truncate text-xs'>
                    {cut(t.kind === 'cron' && t.prompt ? 'AI 措辞：' + t.prompt : t.content)}
                    {t.lastError && <div className='text-destructive text-xs'>错误：{cut(t.lastError, 60)}</div>}
                  </TableCell>
                  <TableCell className='text-muted-foreground text-xs'>
                    {t.kind === 'reminder' ? fmt(t.fireAt) : 'cron ' + (t.cron ?? '') + ' · 下次 ' + fmt(t.nextFireAt)}
                  </TableCell>
                  <TableCell>
                    <Badge variant={
                      t.status === 'done' ? 'default' : t.status === 'failed' ? 'destructive'
                        : t.status === 'pending' ? 'secondary' : 'outline'
                    }>{t.status}</Badge>
                  </TableCell>
                  <TableCell className='text-muted-foreground text-xs'>{t.createdBy}</TableCell>
                  <TableCell className='space-x-1'>
                    {t.status === 'pending' && (
                      <>
                        <Button size='sm' variant='ghost' onClick={() => fire(t.id)}>立即执行</Button>
                        <Button size='sm' variant='destructive' onClick={() => cancel(t.id)}>取消</Button>
                      </>
                    )}
                  </TableCell>
                </TableRow>
              ))}
              {(tasks.data || []).length === 0 && (
                <TableRow>
                  <TableCell colSpan={7} className='text-muted-foreground text-center text-xs'>
                    暂无任务；在 IM 里对机器人说「明早 8 点提醒我…」也可以建任务
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  )
}
