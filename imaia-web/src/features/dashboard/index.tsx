import { useQuery } from '@tanstack/react-query'
import {
  CalendarClock,
  MessagesSquare,
  MessageSquare,
  Plug,
  type LucideIcon,
} from 'lucide-react'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { ProfileDropdown } from '@/components/profile-dropdown'
import { ThemeSwitch } from '@/components/theme-switch'
import { api } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { t } from '@/lib/copy'
import { RecentActivity } from './components/recent-activity'

function StatCard({
  title,
  value,
  hint,
  icon: Icon,
}: {
  title: string
  value?: number
  hint?: string
  icon: LucideIcon
}) {
  return (
    <Card>
      <CardHeader className='flex flex-row items-center justify-between space-y-0 pb-2'>
        <CardTitle className='text-sm font-medium'>{title}</CardTitle>
        <Icon className='size-4 text-muted-foreground' />
      </CardHeader>
      <CardContent>
        <div className='text-2xl font-bold tabular-nums'>{value ?? '—'}</div>
        {hint ? (
          <p className='text-xs text-muted-foreground'>{hint}</p>
        ) : null}
      </CardContent>
    </Card>
  )
}

export function Dashboard() {
  const overview = useQuery({ queryKey: ['overview'], queryFn: api.overview })
  const runtime = useQuery({ queryKey: ['runtime'], queryFn: api.runtime })

  const capabilities = runtime.data
    ? [
        { label: '聊天模型', ok: runtime.data.chat },
        { label: '图像生成', ok: runtime.data.images },
        { label: '视频生成', ok: runtime.data.video },
        { label: '网络搜索', ok: runtime.data.search },
      ]
    : []

  return (
    <>
      <Header fixed>
        <div className='ms-auto flex items-center gap-2'>
          <ThemeSwitch />
          <ProfileDropdown />
        </div>
      </Header>

      <Main className='flex flex-1 flex-col gap-4 sm:gap-6'>
        {overview.isError || runtime.isError ? (
          <div
            role='alert'
            className='flex items-center justify-between gap-3 rounded-md border border-destructive/40 bg-destructive/5 px-4 py-3'
          >
            <span className='text-sm text-muted-foreground'>
              {t('state.loadFailed')}
            </span>
            <Button
              variant='outline'
              size='sm'
              onClick={() => {
                void overview.refetch()
                void runtime.refetch()
              }}
            >
              {t('action.retry')}
            </Button>
          </div>
        ) : null}
        <div>
          <h2 className='text-2xl font-bold tracking-tight'>概览</h2>
          <p className='text-muted-foreground'>
            聚合消息、AI 助手与自动化任务的实时运行状态。
          </p>
        </div>

        {overview.data && !overview.data.assistantEnabled ? (
          <Alert>
            <AlertTitle>AI 助手已停用</AlertTitle>
            <AlertDescription>
              助手总开关没有打开，消息不会触发回复。到「设置」里开启。
            </AlertDescription>
          </Alert>
        ) : null}
        {runtime.data && !runtime.data.chat ? (
          <Alert>
            <AlertTitle>AI 聊天通道未就绪</AlertTitle>
            <AlertDescription>
              还没有可用的聊天模型。到「供应商」接入供应商并同步模型后，助手才能回复。
            </AlertDescription>
          </Alert>
        ) : null}

        <div className='grid gap-4 sm:grid-cols-2 lg:grid-cols-4'>
          <StatCard
            title='平台账号'
            value={overview.data?.accounts}
            hint={
              runtime.data
                ? '已连接平台 ' +
                  runtime.data.platforms.filter((p) => p.configured).length +
                  ' / ' +
                  runtime.data.platforms.length
                : undefined
            }
            icon={Plug}
          />
          <StatCard
            title='会话'
            value={overview.data?.conversations}
            icon={MessagesSquare}
          />
          <StatCard
            title='今日消息'
            value={overview.data?.messagesToday}
            hint={
              overview.data
                ? '累计 ' + overview.data.messagesTotal
                : undefined
            }
            icon={MessageSquare}
          />
          <StatCard
            title='待执行任务'
            value={overview.data?.pendingTasks}
            hint={
              overview.data
                ? '今日已处理 ' + overview.data.processedToday
                : undefined
            }
            icon={CalendarClock}
          />
        </div>

        <div className='grid grid-cols-1 gap-4 lg:grid-cols-7'>
          <Card className='col-span-1 lg:col-span-4'>
            <CardHeader>
              <CardTitle>最近动态</CardTitle>
              <CardDescription>最新归档的聚合消息。</CardDescription>
            </CardHeader>
            <CardContent>
              <RecentActivity />
            </CardContent>
          </Card>

          <Card className='col-span-1 lg:col-span-3'>
            <CardHeader>
              <CardTitle>能力状态</CardTitle>
              <CardDescription>AI 工具与平台账号的就绪情况。</CardDescription>
            </CardHeader>
            <CardContent className='space-y-3'>
              {capabilities.map((c) => (
                <div key={c.label} className='flex items-center justify-between'>
                  <span className='text-sm'>{c.label}</span>
                  <Badge variant={c.ok ? 'default' : 'outline'}>
                    {c.ok ? '就绪' : '未配置'}
                  </Badge>
                </div>
              ))}
              <div className='border-t pt-3' />
              {runtime.data
                ? runtime.data.platforms.map((p) => (
                    <div
                      key={p.platformCode}
                      className='flex items-center justify-between'
                    >
                      <span className='text-sm capitalize'>{p.platformCode}</span>
                      <Badge variant={p.configured ? 'default' : 'outline'}>
                        {p.configured ? '已连接' : '未配置'}
                      </Badge>
                    </div>
                  ))
                : null}
            </CardContent>
          </Card>
        </div>
      </Main>
    </>
  )
}
