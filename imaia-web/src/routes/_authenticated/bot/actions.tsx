import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'

const fmt = (t: string | null) => (t ? new Date(t).toLocaleString('zh-CN', { hour12: false }) : '')
const cut = (s: string | null, n = 80) => (s ? (s.length > n ? s.slice(0, n) + '…' : s) : '—')
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api } from '@/features/imaia/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Textarea } from '@/components/ui/textarea'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
// actions
export const Route = createFileRoute('/_authenticated/bot/actions')({
  component: ActionsPage,
})

function ActionsPage() {
  const qc = useQueryClient();
  const { data: list, isLoading } = useQuery({ queryKey: ['actions'], queryFn: api.actions });
  const [form, setForm] = useState({ threadId: '', action: 'send_text', content: '', prompt: '', fireAt: '', cron: '' });
  const create = () => {
    api.createAction({
      threadId: form.threadId, action: form.action,
      content: form.action === 'send_text' ? form.content : null,
      prompt: form.action === 'send_ai_prompt' ? form.prompt : null,
      fireAt: form.fireAt ? new Date(form.fireAt).toISOString() : null,
      cron: form.cron || null,
    })
      .then(() => { toast.success('已创建'); setForm({ threadId: '', action: 'send_text', content: '', prompt: '', fireAt: '', cron: '' }); qc.invalidateQueries({ queryKey: ['actions'] }); })
      .catch((e: Error) => toast.error(e.message));
  };
  const cancel = (id: number) => api.cancelAction(id).then(() => qc.invalidateQueries({ queryKey: ['actions'] })).catch((e: Error) => toast.error(e.message));
  const fire = (id: number) => api.fireAction(id).then(() => qc.invalidateQueries({ queryKey: ['actions'] })).catch((e: Error) => toast.error(e.message));
  return (
    <div className='space-y-4'>
      <Card>
        <CardHeader><CardTitle className='text-sm'>新建计划任务（强制绑定会话：AI 只发往明确目标）</CardTitle></CardHeader>
        <CardContent className='space-y-3'>
          <div className='grid grid-cols-2 gap-4'>
            <Input placeholder='目标会话 threadId' value={form.threadId} onChange={(e) => setForm({ ...form, threadId: e.target.value })} />
            <select className='border-input bg-background rounded-md border px-3 py-2 text-sm' value={form.action} onChange={(e) => setForm({ ...form, action: e.target.value })}>
              <option value='send_text'>发送固定文本</option>
              <option value='send_ai_prompt'>AI 生成后发送</option>
            </select>
            {form.action === 'send_text'
              ? <Input placeholder='内容' className='col-span-2' value={form.content} onChange={(e) => setForm({ ...form, content: e.target.value })} />
              : <Input placeholder='AI 指令 prompt' className='col-span-2' value={form.prompt} onChange={(e) => setForm({ ...form, prompt: e.target.value })} />}
            <Input placeholder='一次性时间（ISO，如 2030-01-01T08:00:00Z）' value={form.fireAt} onChange={(e) => setForm({ ...form, fireAt: e.target.value })} />
            <Input placeholder='cron（周期性，二选一）' value={form.cron} onChange={(e) => setForm({ ...form, cron: e.target.value })} />
          </div>
          <Button onClick={create}>创建</Button>
        </CardContent>
      </Card>
      <Card>
        <CardContent>
          <Table>
            <TableHeader><TableRow><TableHead>会话</TableHead><TableHead>动作</TableHead><TableHead>内容/指令</TableHead><TableHead>时间</TableHead><TableHead>状态</TableHead><TableHead>操作</TableHead></TableRow></TableHeader>
            <TableBody>
              {(list || []).map((a) => (
                <TableRow key={a.id}>
                  <TableCell className='text-xs'>{a.threadId}</TableCell>
                  <TableCell><Badge variant='outline'>{a.action}</Badge></TableCell>
                  <TableCell className='text-muted-foreground max-w-xs truncate text-xs'>{cut(a.content || a.prompt)}</TableCell>
                  <TableCell className='text-muted-foreground text-xs'>{fmt(a.fireAt) || a.cron}</TableCell>
                  <TableCell><Badge variant={a.status === 'done' ? 'default' : a.status === 'failed' ? 'destructive' : a.status === 'pending' ? 'secondary' : 'outline'}>{a.status}</Badge></TableCell>
                  <TableCell className='space-x-1'>
                    {a.status === 'pending' && <Button size='sm' variant='ghost' onClick={() => fire(a.id)}>立即执行</Button>}
                    {a.status === 'pending' && <Button size='sm' variant='destructive' onClick={() => cancel(a.id)}>取消</Button>}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}
