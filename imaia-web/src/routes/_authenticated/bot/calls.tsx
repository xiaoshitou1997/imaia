import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/features/imaia/api'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'

export const Route = createFileRoute('/_authenticated/bot/calls')({
  component: CallsPage,
})

const fmt = (t: string | null | undefined) => (t ? new Date(t).toLocaleString('zh-CN', { hour12: false }) : '—')

function CallsPage() {
  const { data, isLoading } = useQuery({ queryKey: ['calls'], queryFn: api.calls })
  if (isLoading) return <div className='text-muted-foreground'>加载中…</div>
  return (
    <Card>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>时间</TableHead>
              <TableHead>用途</TableHead>
              <TableHead>接入商</TableHead>
              <TableHead>模型</TableHead>
              <TableHead>输入/输出 Tokens</TableHead>
              <TableHead>耗时</TableHead>
              <TableHead>结果</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {(data || []).map((c) => (
              <TableRow key={c.id}>
                <TableCell className='text-muted-foreground text-xs'>{fmt(c.createdAt)}</TableCell>
                <TableCell className='text-xs'>{c.purpose}</TableCell>
                <TableCell className='text-muted-foreground text-xs'>{c.providerName ?? '—'}</TableCell>
                <TableCell className='text-xs'>{c.modelId ?? '—'}</TableCell>
                <TableCell className='text-muted-foreground text-xs'>
                  {c.promptTokens ?? '—'} / {c.completionTokens ?? '—'}
                </TableCell>
                <TableCell className='text-muted-foreground text-xs'>
                  {c.durationMs == null ? '—' : (c.durationMs / 1000).toFixed(1) + 's'}
                </TableCell>
                <TableCell>
                  <Badge variant={c.ok ? 'default' : 'destructive'}>
                    {c.ok ? 'ok' : (c.error ? '失败：' + c.error.slice(0, 60) : '失败')}
                  </Badge>
                </TableCell>
              </TableRow>
            ))}
            {(data || []).length === 0 && (
              <TableRow>
                <TableCell colSpan={7} className='text-muted-foreground text-center text-xs'>暂无调用</TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}
