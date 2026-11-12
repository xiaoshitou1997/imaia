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
// calls
export const Route = createFileRoute('/_authenticated/bot/calls')({
  component: CallsPage,
})

function CallsPage() {
  const { data: list, isLoading } = useQuery({ queryKey: ['calls'], queryFn: api.calls, refetchInterval: 10000 });
  if (isLoading) return <div className='text-muted-foreground'>加载中…</div>;
  return (
    <Card>
      <CardContent>
        <Table>
          <TableHeader><TableRow><TableHead>时间</TableHead><TableHead>用途</TableHead><TableHead>模型</TableHead><TableHead>tokens</TableHead><TableHead>耗时</TableHead><TableHead>结果</TableHead></TableRow></TableHeader>
          <TableBody>
            {(list || []).map((c, i) => (
              <TableRow key={i}>
                <TableCell className='text-muted-foreground text-xs'>{fmt(c.createdAt)}</TableCell>
                <TableCell><Badge variant='outline'>{c.purpose}</Badge></TableCell>
                <TableCell className='text-muted-foreground text-xs'>{c.providerName}/{c.modelId}</TableCell>
                <TableCell className='text-muted-foreground text-xs'>{(c.promptTokens || 0) + (c.completionTokens || 0)}</TableCell>
                <TableCell className='text-muted-foreground text-xs'>{c.durationMs}ms</TableCell>
                <TableCell><Badge variant={c.ok ? 'default' : 'destructive'}>{c.ok ? '✓' : cut(c.error, 40)}</Badge></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
