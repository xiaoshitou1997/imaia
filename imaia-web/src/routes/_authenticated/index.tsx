import { createFileRoute, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/features/imaia/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'

export const Route = createFileRoute('/_authenticated/')({
  component: OverviewPage,
})

function OverviewPage() {
  const overview = useQuery({ queryKey: ['overview'], queryFn: api.overview, refetchInterval: 15000 })
  const runtime = useQuery({ queryKey: ['runtime'], queryFn: api.runtime })
  if (overview.isLoading || !overview.data) {
    return <div className='text-muted-foreground'>加载中…</div>
  }
  const o = overview.data
  const stats: [string, number][] = [
    ['接入连接', o.connections],
    ['会话', o.conversations],
    ['消息总数', o.messagesTotal],
    ['今日消息', o.messagesToday],
    ['今日已处理', o.processedToday],
    ['待执行任务', o.pendingTasks],
    ['长期记忆', o.memories],
    ['AI 产物', o.artifacts],
    ['模型调用', o.calls],
    ['洞察报告', o.reports],
  ]
  const runtimeData = runtime.data ?? {}
  return (
    <div className='space-y-4'>
      {!o.assistantEnabled && (
        <Card className='border-amber-500/50'>
          <CardContent className='text-sm'>
            AI 总开关当前是关闭的——所有消息只归档不回复。
            <Link to='/bot/settings' className='text-foreground ml-1 underline'>去设置开启 →</Link>
          </CardContent>
        </Card>
      )}

      <div className='grid grid-cols-2 gap-4 md:grid-cols-5'>
        {stats.map(([label, value]) => (
          <Card key={label}>
            <CardHeader className='pb-1'>
              <CardTitle className='text-muted-foreground text-xs font-normal'>{label}</CardTitle>
            </CardHeader>
            <CardContent>
              <div className='text-2xl font-bold'>{value}</div>
            </CardContent>
          </Card>
        ))}
      </div>

      <Card>
        <CardHeader className='pb-2'>
          <CardTitle className='text-sm'>能力就绪状态</CardTitle>
        </CardHeader>
        <CardContent className='flex flex-wrap gap-2'>
          <Badge variant={runtimeData.chat ? 'default' : 'destructive'}>对话模型</Badge>
          <Badge variant={runtimeData.images ? 'default' : 'secondary'}>生图</Badge>
          <Badge variant={runtimeData.video ? 'default' : 'secondary'}>生视频</Badge>
          <Badge variant={runtimeData.search ? 'default' : 'secondary'}>网页搜索</Badge>
          {(Array.isArray(runtimeData.platforms) ? runtimeData.platforms : []).map((p) => {
            const platform = p as { platform: string; configured: boolean }
            return (
              <Badge key={platform.platform} variant={platform.configured ? 'default' : 'secondary'}>
                {platform.platform} {platform.configured ? '已接入' : '未配置'}
              </Badge>
            )
          })}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className='pb-2'>
          <CardTitle className='text-sm'>快速入口</CardTitle>
        </CardHeader>
        <CardContent className='flex flex-wrap gap-3 text-sm'>
          <Link to='/bot/inbox' className='underline'>统一收件箱</Link>
          <Link to='/bot/tasks' className='underline'>定时任务</Link>
          <Link to='/bot/reports' className='underline'>洞察报表</Link>
          <Link to='/bot/connections' className='underline'>接入连接</Link>
          <Link to='/bot/models' className='underline'>模型目录</Link>
        </CardContent>
      </Card>
    </div>
  )
}
