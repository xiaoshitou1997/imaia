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
// contacts
export const Route = createFileRoute('/_authenticated/bot/contacts')({
  component: ContactsPage,
})

function ContactsPage() {
  const qc = useQueryClient();
  const { data: list, isLoading } = useQuery({ queryKey: ['contacts'], queryFn: api.contacts });
  const [query, setQuery] = useState('');
  const [editing, setEditing] = useState<{ id: number; persona: string; remark: string } | null>(null);
  const [analysis, setAnalysis] = useState<{ contactId: number; profile: string } | null>(null);
  const [analyzing, setAnalyzing] = useState(false);
  const filtered = (list || []).filter((c) => !query || (c.displayName + c.wxid).toLowerCase().includes(query.toLowerCase()));
  const savePersona = () => {
    if (!editing) return;
    api.updateContact(editing.id, { persona: editing.persona, remark: editing.remark })
      .then(() => { toast.success('已保存，下一轮对话生效'); setEditing(null); qc.invalidateQueries({ queryKey: ['contacts'] }); })
      .catch((e: Error) => toast.error(e.message));
  };
  const analyze = (contactId: number) => {
    setAnalyzing(true);
    api.runAnalysis(contactId)
      .then((res) => { setAnalysis({ contactId, profile: res.profile }); setAnalyzing(false); })
      .catch((e: Error) => { toast.error(e.message); setAnalyzing(false); });
  };
  return (
    <div className='space-y-4'>
      <Input placeholder='搜索会话…' value={query} onChange={(e) => setQuery(e.target.value)} className='max-w-sm' />
      <Card>
        <CardContent>
          <Table>
            <TableHeader><TableRow><TableHead>名称</TableHead><TableHead>wxid</TableHead><TableHead>类型</TableHead><TableHead>最近活跃</TableHead><TableHead>风格 persona</TableHead><TableHead>操作</TableHead></TableRow></TableHeader>
            <TableBody>
              {filtered.map((c) => (
                <TableRow key={c.id}>
                  <TableCell>{c.displayName}</TableCell>
                  <TableCell className='text-muted-foreground text-xs'>{c.wxid}</TableCell>
                  <TableCell><Badge variant={c.kind === 'group' ? 'outline' : 'secondary'}>{c.kind}</Badge></TableCell>
                  <TableCell className='text-muted-foreground text-xs'>{fmt(c.lastSeenAt)}</TableCell>
                  <TableCell className='text-muted-foreground max-w-xs truncate text-xs'>{c.persona || '—'}</TableCell>
                  <TableCell className='space-x-1'>
                    <Button size='sm' variant='ghost' onClick={() => setEditing({ id: c.id, persona: c.persona || '', remark: c.remark || '' })}>编辑</Button>
                    <Button size='sm' variant='ghost' onClick={() => { setAnalysis({ contactId: c.id, profile: '' }); analyze(c.id); }}>画像</Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
      {editing && (
        <Card>
          <CardHeader><CardTitle className='text-sm'>编辑会话风格 persona（下一轮对话生效）</CardTitle></CardHeader>
          <CardContent className='space-y-3'>
            <Textarea value={editing.persona} onChange={(e) => setEditing({ ...editing, persona: e.target.value })} />
            <Input placeholder='备注' value={editing.remark} onChange={(e) => setEditing({ ...editing, remark: e.target.value })} />
            <div className='space-x-2'>
              <Button onClick={savePersona}>保存</Button>
              <Button variant='ghost' onClick={() => setEditing(null)}>取消</Button>
            </div>
          </CardContent>
        </Card>
      )}
      {analysis && (
        <Card>
          <CardHeader><CardTitle className='text-sm'>人物画像</CardTitle></CardHeader>
          <CardContent>
            <div className='whitespace-pre-wrap text-sm'>{analyzing ? '分析中…（读取归档并调用模型）' : analysis.profile}</div>
            <Button size='sm' variant='ghost' className='mt-2' disabled={analyzing} onClick={() => analyze(analysis.contactId)}>重新分析</Button>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
