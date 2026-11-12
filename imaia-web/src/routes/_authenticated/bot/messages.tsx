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
// messages
export const Route = createFileRoute('/_authenticated/bot/messages')({
  component: MessagesPage,
})

function MessagesPage() {
  const [threadId, setThreadId] = useState('');
  const [submitted, setSubmitted] = useState('');
  const [sendText, setSendText] = useState('');
  const { data: rows, isLoading } = useQuery({
    queryKey: ['messages', submitted],
    queryFn: () => api.messages(submitted),
    enabled: !!submitted,
    refetchInterval: 15000,
  });
  const send = () => {
    const text = sendText.trim();
    if (!text || !submitted) return;
    api.sendText(submitted, text)
      .then(() => { toast.success('已发送'); setSendText(''); })
      .catch((e: Error) => toast.error(e.message));
  };
  return (
    <div className='space-y-4'>
      <div className='flex gap-2'>
        <Input placeholder='threadId（wxid 或 @chatroom）' value={threadId} onChange={(e) => setThreadId(e.target.value)} className='max-w-sm' />
        <Button onClick={() => setSubmitted(threadId.trim())}>查询</Button>
      </div>
      {isLoading && submitted && <div className='text-muted-foreground'>加载中…</div>}
      {rows && rows.length > 0 && (
        <Card>
          <CardContent>
            {rows.map((m, i) => (
              <div key={i} className='flex gap-3 border-b py-1.5 text-sm last:border-b-0'>
                <span className='text-muted-foreground w-32 shrink-0 text-xs'>{fmt(m.time)}</span>
                <span className={'w-24 shrink-0 text-xs ' + (m.direction.startsWith('ai') ? 'text-green-500' : 'text-blue-400')}>{m.direction === 'ai-tool' ? '🔧 工具' : (m.senderName || m.direction)}</span>
                <span className='text-muted-foreground w-14 shrink-0 text-xs'>{m.type}</span>
                <span className='min-w-0 break-all whitespace-pre-wrap'>{m.body}</span>
              </div>
            ))}
          </CardContent>
        </Card>
      )}
      {submitted && (
        <div className='flex gap-2'>
          <Input placeholder={'直接对话：真实发送微信消息到 ' + submitted + '…'} value={sendText} onChange={(e) => setSendText(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') send(); }} />
          <Button onClick={send}>发送</Button>
        </div>
      )}
    </div>
  );
}
