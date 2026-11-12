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
// memories
export const Route = createFileRoute('/_authenticated/bot/memories')({
  component: MemoriesPage,
})

function MemoriesPage() {
  const qc = useQueryClient();
  const { data: list, isLoading } = useQuery({ queryKey: ['memories'], queryFn: api.memories });
  const save = (m) => api.updateMemory(m.id, m.fact).then(() => toast.success('已保存')).catch((e: Error) => toast.error(e.message));
  const remove = (id: number) => { if (confirm('删除这条记忆？')) api.deleteMemory(id).then(() => qc.invalidateQueries({ queryKey: ['memories'] })).catch((e: Error) => toast.error(e.message)); };
  const promote = (id: number) => api.promoteMemory(id).then(() => { toast.success('已提升为全局'); qc.invalidateQueries({ queryKey: ['memories'] }); }).catch((e: Error) => toast.error(e.message));
  if (isLoading) return <div className='text-muted-foreground'>加载中…</div>;
  return (
    <Card>
      <CardContent>
        <Table>
          <TableHeader><TableRow><TableHead>scope</TableHead><TableHead>kind</TableHead><TableHead>事实（可直接编辑）</TableHead><TableHead>状态</TableHead><TableHead>操作</TableHead></TableRow></TableHeader>
          <TableBody>
            {(list || []).map((m) => (
              <TableRow key={m.id}>
                <TableCell><Badge variant={m.scope === 'global' ? 'default' : 'secondary'}>{m.scope}</Badge></TableCell>
                <TableCell><Badge variant='outline'>{m.kind}</Badge></TableCell>
                <TableCell>
                  <Input value={m.fact} onChange={(e) => { m.fact = e.target.value; }} onBlur={() => save(m)} />
                  <div className='text-muted-foreground text-xs'>{fmt(m.createdAt)}{m.supersedesId ? ' · 取代 #' + m.supersedesId : ''}</div>
                </TableCell>
                <TableCell><Badge variant={m.status === 'active' ? 'default' : 'secondary'}>{m.status}</Badge></TableCell>
                <TableCell className='space-x-1'>
                  {m.scope !== 'global' && <Button size='sm' variant='ghost' onClick={() => promote(m.id)}>提升为全局</Button>}
                  <Button size='sm' variant='destructive' onClick={() => remove(m.id)}>删除</Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
