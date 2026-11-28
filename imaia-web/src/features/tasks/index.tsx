import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { ProfileDropdown } from '@/components/profile-dropdown'
import { ThemeSwitch } from '@/components/theme-switch'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { TasksDialogs } from './components/tasks-dialogs'
import { TasksPrimaryButtons } from './components/tasks-primary-buttons'
import { TasksProvider } from './components/tasks-provider'
import { TasksTable } from './components/tasks-table'
import { taskStatusOptions } from './data/status'
import { Route } from '@/routes/_authenticated/tasks/'

export function Tasks() {
  // 筛选与页码来自路由 search：URL 是唯一事实源，刷新与分享不丢。
  const search = Route.useSearch()
  const navigate = Route.useNavigate()
  const status = search.status ?? 'all'

  return (
    <TasksProvider>
      <Header fixed>
        <div className='ms-auto flex items-center gap-2'>
          <ThemeSwitch />
          <ProfileDropdown />
        </div>
      </Header>

      <Main className='flex flex-1 flex-col gap-4 sm:gap-6'>
        <div className='flex flex-wrap items-end justify-between gap-2'>
          <div>
            <h2 className='text-2xl font-bold tracking-tight'>定时任务</h2>
            <p className='text-muted-foreground'>
              到点或按周期向会话发送消息：prompt 有则 AI 措辞，无则直发 content。
            </p>
          </div>
          <div className='flex items-center gap-2'>
            <Select
              value={status}
              onValueChange={(v) => navigate({ search: (prev) => ({ ...prev, status: v === 'all' ? undefined : v }) })}
            >
              <SelectTrigger className='w-32'>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value='all'>全部状态</SelectItem>
                {taskStatusOptions.map((s) => (
                  <SelectItem key={s.value} value={s.value}>
                    {s.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <TasksPrimaryButtons />
          </div>
        </div>

        <TasksTable status={status === 'all' ? undefined : status} />
      </Main>

      <TasksDialogs />
    </TasksProvider>
  )
}
