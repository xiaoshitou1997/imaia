import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/features/imaia/api'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'

export const Route = createFileRoute('/_authenticated/bot/artifacts')({
  component: ArtifactsPage,
})

const fmt = (t: string | null | undefined) => (t ? new Date(t).toLocaleString('zh-CN', { hour12: false }) : '—')
const cut = (s: string | null | undefined, n = 80) => (s ? (s.length > n ? s.slice(0, n) + '…' : s) : '—')

function ArtifactsPage() {
  const { data, isLoading } = useQuery({ queryKey: ['artifacts'], queryFn: api.artifacts })
  if (isLoading) return <div className='text-muted-foreground'>加载中…</div>
  return (
    <Card>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>会话</TableHead>
              <TableHead>类型</TableHead>
              <TableHead>提示词</TableHead>
              <TableHead>文件</TableHead>
              <TableHead>状态</TableHead>
              <TableHead>时间</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {(data || []).map((a) => (
              <TableRow key={a.id}>
                <TableCell className='text-muted-foreground text-xs'>{a.conversationId}</TableCell>
                <TableCell><Badge variant='outline'>{a.kind}</Badge></TableCell>
                <TableCell className='text-muted-foreground max-w-md truncate text-xs'>{cut(a.prompt)}</TableCell>
                <TableCell className='text-xs'>{a.filename ?? '—'}</TableCell>
                <TableCell>
                  <Badge variant={a.status === 'delivered' ? 'default' : a.status === 'failed' ? 'destructive' : 'secondary'}>
                    {a.status}
                  </Badge>
                </TableCell>
                <TableCell className='text-muted-foreground text-xs'>{fmt(a.createdAt)}</TableCell>
              </TableRow>
            ))}
            {(data || []).length === 0 && (
              <TableRow>
                <TableCell colSpan={6} className='text-muted-foreground text-center text-xs'>
                  暂无产物；让 AI 画图或生成视频后会登记在这里
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}
