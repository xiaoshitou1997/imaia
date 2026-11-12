import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { api, type Overview } from '@/features/imaia/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'

export const Route = createFileRoute('/_authenticated/')({
  component: OverviewPage,
})

function OverviewPage() {
  const { data, isLoading } = useQuery({ queryKey: ['overview'], queryFn: api.overview, refetchInterval: 15000 })
  if (isLoading || !data) return <div className='text-muted-foreground'>加载中…</div>
  const o = data as Overview
  const stats: [string, unknown][] = [
    ['今日消息', o.messagesToday],
    ['AI 回复', o.aiRepliesToday],
    ['今日 Token', o.tokensToday],
    ['待办任务', o.pendingActions],
  ]
  return (
    <div className='space-y-4'>
      <div className='grid grid-cols-2 gap-4 md:grid-cols-4'>
        {stats.map(([label, value]) => (
          <Card key={label}>
            <CardHeader className='pb-1'><CardTitle className='text-muted-foreground text-xs font-normal'>{label}</CardTitle></CardHeader>
            <CardContent><div className='text-2xl font-bold'>{String(value)}</div></CardContent>
          </Card>
        ))}
      </div>
      <Card>
        <CardHeader><CardTitle className='text-sm'>最近归档消息</CardTitle></CardHeader>
        <CardContent>
          <Table>
            <TableHeader><TableRow><TableHead>时间</TableHead><TableHead>会话</TableHead><TableHead>方向</TableHead><TableHead>类型</TableHead><TableHead>内容</TableHead></TableRow></TableHeader>
            <TableBody>
              {o.recent.map((r, i) => (
                <TableRow key={i}>
                  <TableCell className='text-muted-foreground text-xs'>{new Date(r.time).toLocaleString('zh-CN', { hour12: false })}</TableCell>
                  <TableCell className='text-xs'>{r.threadId}</TableCell>
                  <TableCell><Badge variant={r.direction === 'incoming' ? 'secondary' : 'default'}>{r.direction}</Badge></TableCell>
                  <TableCell className='text-xs'>{r.type}</TableCell>
                  <TableCell className='max-w-md truncate text-xs'>{r.body}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  )
}
