import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
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
// console
export const Route = createFileRoute('/_authenticated/bot/console')({
  component: ConsolePage,
})

function ConsolePage() {
  const [question, setQuestion] = useState('');
  const [reply, setReply] = useState('');
  const ask = () => {
    const q = question.trim();
    if (!q) return;
    setReply('思考中…');
    api.ask(q).then((res) => setReply(res.reply || '(无回复)')).catch((e) => setReply('出错：' + String(e)));
  };
  return (
    <Card>
      <CardHeader><CardTitle className='text-sm'>与 AI 直接对话（本地控制台，独立 local-console 会话）</CardTitle></CardHeader>
      <CardContent className='space-y-3'>
        <Textarea value={question} onChange={(e) => setQuestion(e.target.value)} placeholder='输入问题…' />
        <Button onClick={ask}>发送</Button>
        {reply && <div className='rounded-md border p-3 text-sm whitespace-pre-wrap'>{reply}</div>}
      </CardContent>
    </Card>
  );
}
