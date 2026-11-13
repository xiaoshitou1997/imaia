import { createFileRoute } from '@tanstack/react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api } from '@/features/imaia/api'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'

export const Route = createFileRoute('/_authenticated/bot/memories')({
  component: MemoriesPage,
})

const fmt = (t: string | null | undefined) => (t ? new Date(t).toLocaleString('zh-CN', { hour12: false }) : '—')

function MemoriesPage() {
  const qc = useQueryClient()
  const memories = useQuery({ queryKey: ['memories'], queryFn: api.memories })

  const invalidate = () => qc.invalidateQueries({ queryKey: ['memories'] })

  const save = (id: number, fact: string) =>
    api.updateMemory(id, fact)
      .then(() => toast.success('已保存，下一轮对话即生效'))
      .catch((e: Error) => toast.error(e.message))

  const remove = (id: number) => {
    if (!confirm('删除这条记忆？')) return
    api.deleteMemory(id).then(() => { toast.success('已删除'); invalidate() }).catch((e: Error) => toast.error(e.message))
  }

  return (
    <Card>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>会话</TableHead>
              <TableHead>事实（可直接编辑，失焦保存）</TableHead>
              <TableHead>记录时间</TableHead>
              <TableHead>操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {(memories.data || []).map((m) => (
              <TableRow key={m.id}>
                <TableCell>
                  <Badge variant='secondary'>{m.conversationLabel}</Badge>
                </TableCell>
                <TableCell>
                  <Input
                    defaultValue={m.fact}
                    onBlur={(e) => {
                      const next = e.target.value.trim()
                      if (next && next !== m.fact) save(m.id, next)
                    }}
                  />
                  <div className='text-muted-foreground mt-1 text-xs'>{fmt(m.createdAt)}</div>
                </TableCell>
                <TableCell className='text-muted-foreground text-xs'>{fmt(m.createdAt)}</TableCell>
                <TableCell>
                  <Button size='sm' variant='destructive' onClick={() => remove(m.id)}>删除</Button>
                </TableCell>
              </TableRow>
            ))}
            {(memories.data || []).length === 0 && (
              <TableRow>
                <TableCell colSpan={4} className='text-muted-foreground text-center text-xs'>
                  暂无记忆；对话里让 AI「记住……」即可沉淀（v1 仅 AI 自动沉淀、挂会话线）
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}
