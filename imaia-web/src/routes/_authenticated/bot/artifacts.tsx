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
// artifacts
export const Route = createFileRoute('/_authenticated/bot/artifacts')({
  component: ArtifactsPage,
})

function ArtifactsPage() {
  const { data: list, isLoading } = useQuery({ queryKey: ['artifacts'], queryFn: api.artifacts });
  if (isLoading) return <div className='text-muted-foreground'>加载中…</div>;
  return (
    <Card>
      <CardContent>
        <Table>
          <TableHeader><TableRow><TableHead>时间</TableHead><TableHead>会话</TableHead><TableHead>类型</TableHead><TableHead>模型</TableHead><TableHead>prompt</TableHead><TableHead>状态</TableHead></TableRow></TableHeader>
          <TableBody>
            {(list || []).map((a) => (
              <TableRow key={a.id}>
                <TableCell className='text-muted-foreground text-xs'>{fmt(a.createdAt)}</TableCell>
                <TableCell className='text-xs'>{a.threadId}</TableCell>
                <TableCell><Badge variant='outline'>{a.kind}</Badge></TableCell>
                <TableCell className='text-muted-foreground text-xs'>{a.modelId || '—'}</TableCell>
                <TableCell className='text-muted-foreground max-w-md truncate text-xs'>{cut(a.prompt)}</TableCell>
                <TableCell><Badge variant={a.status === 'delivered' ? 'default' : a.status === 'failed' ? 'destructive' : 'secondary'}>{a.status}</Badge></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
