import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api, type ReportRow } from '@/features/imaia/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'

export const Route = createFileRoute('/_authenticated/bot/reports')({
  component: ReportsPage,
})

const fmt = (t: string | null | undefined) => (t ? new Date(t).toLocaleString('zh-CN', { hour12: false }) : '—')

const PERIOD_LABEL: Record<string, string> = { week: '周报', month: '月报', year: '年报' }

function ReportsPage() {
  const qc = useQueryClient()
  const [conversationId, setConversationId] = useState('')
  const [periodType, setPeriodType] = useState('week')
  const [openId, setOpenId] = useState<number | null>(null)

  const conversations = useQuery({ queryKey: ['conversations', ''], queryFn: () => api.conversations() })
  const reports = useQuery({
    queryKey: ['reports', conversationId],
    queryFn: () => api.reports(conversationId ? Number(conversationId) : undefined),
  })

  const generate = () => {
    if (!conversationId) {
      toast.error('请选择会话')
      return
    }
    api.createReport({ conversationId: Number(conversationId), periodType })
      .then((report) => {
        toast.success('报告已生成' + (report.aiReport ? '' : '（AI 评析暂不可用，仅统计）'))
        qc.invalidateQueries({ queryKey: ['reports'] })
        setOpenId(report.id)
      })
      .catch((e: Error) => toast.error(e.message))
  }

  return (
    <div className='space-y-4'>
      <Card>
        <CardHeader className='pb-2'>
          <CardTitle className='text-sm'>生成报告（统计 + AI 评析；同一会话同周期重复生成会覆盖旧报告）</CardTitle>
        </CardHeader>
        <CardContent className='flex flex-wrap items-center gap-3'>
          <select
            className='border-input bg-background rounded-md border px-3 py-2 text-sm'
            value={conversationId}
            onChange={(e) => setConversationId(e.target.value)}
          >
            <option value=''>选择会话…</option>
            {(conversations.data || []).map((c) => (
              <option key={c.id} value={c.id}>
                {c.platform} · {c.displayName || c.externalId}
              </option>
            ))}
          </select>
          <select
            className='border-input bg-background rounded-md border px-3 py-2 text-sm'
            value={periodType}
            onChange={(e) => setPeriodType(e.target.value)}
          >
            <option value='week'>周报</option>
            <option value='month'>月报</option>
            <option value='year'>年报</option>
          </select>
          <Button onClick={generate}>生成</Button>
          <div className='text-muted-foreground text-xs'>
            群内也可以直接 @机器人 说「本周统计 / 月报 / 年报」，报告直接发回群里
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>会话</TableHead>
                <TableHead>周期</TableHead>
                <TableHead>起始</TableHead>
                <TableHead>生成时间</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(reports.data || []).map((r) => (
                <Row key={r.id} report={r} open={openId === r.id} onToggle={() => setOpenId(openId === r.id ? null : r.id)} />
              ))}
              {(reports.data || []).length === 0 && (
                <TableRow>
                  <TableCell colSpan={4} className='text-muted-foreground text-center text-xs'>
                    暂无报告；周期任务也可以自动生成落库
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  )
}

function Row({ report, open, onToggle }: { report: ReportRow; open: boolean; onToggle: () => void }) {
  return (
    <>
      <TableRow className='cursor-pointer' onClick={onToggle}>
        <TableCell className='text-xs'>{report.conversationLabel}</TableCell>
        <TableCell><Badge variant='outline'>{PERIOD_LABEL[report.periodType] ?? report.periodType}</Badge></TableCell>
        <TableCell className='text-muted-foreground text-xs'>{report.periodStart}</TableCell>
        <TableCell className='text-muted-foreground text-xs'>{fmt(report.createdAt)}</TableCell>
      </TableRow>
      {open && (
        <TableRow>
          <TableCell colSpan={4} className='bg-muted/40'>
            {report.aiReport ? (
              <div className='whitespace-pre-wrap text-sm'>{report.aiReport}</div>
            ) : (
              <div className='text-muted-foreground text-sm'>本次生成只有统计，AI 评析不可用。</div>
            )}
          </TableCell>
        </TableRow>
      )}
    </>
  )
}
