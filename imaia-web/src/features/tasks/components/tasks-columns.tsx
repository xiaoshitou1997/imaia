import { type ColumnDef } from '@tanstack/react-table'
import { MoreHorizontal } from 'lucide-react'
import { formatDistanceToNow } from 'date-fns'
import { zhCN } from 'date-fns/locale'
import { toast } from 'sonner'
import { api, ApiError, type TaskRow } from '@/lib/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { taskStatusLabel } from '../data/status'
import { useTasks } from './tasks-provider'

const rel = (v: string | null) =>
  v
    ? formatDistanceToNow(new Date(v), { addSuffix: true, locale: zhCN })
    : '—'

export const tasksColumns: ColumnDef<TaskRow>[] = [
  {
    id: 'id',
    header: 'ID',
    enableSorting: false,
    cell: ({ row }) => (
      <span className='tabular-nums text-muted-foreground'>{row.original.id}</span>
    ),
  },
  {
    id: 'conversation',
    header: '目标会话',
    enableSorting: false,
    meta: { className: 'max-w-40' },
    cell: ({ row }) => (
      <span className='truncate'>
        {row.original.conversationLabel ?? '未知会话'}
      </span>
    ),
  },
  {
    id: 'content',
    header: '内容',
    enableSorting: false,
    meta: { className: 'max-w-0 w-1/3' },
    cell: ({ row }) => (
      <div className='flex items-center gap-2'>
        <Badge variant='outline'>
          {row.original.content ? '直发' : 'AI 措辞'}
        </Badge>
        <span className='truncate text-muted-foreground'>
          {row.original.content || row.original.prompt || '—'}
        </span>
      </div>
    ),
  },
  {
    id: 'schedule',
    header: '调度',
    enableSorting: false,
    cell: ({ row }) => (
      <div className='flex flex-col'>
        {row.original.cronExpr ? (
          <code className='text-xs'>{row.original.cronExpr}</code>
        ) : (
          <span className='text-xs'>
            {row.original.runAt ? row.original.runAt.replace('T', ' ') : '—'}
          </span>
        )}
        <span className='text-xs text-muted-foreground'>
          {row.original.timezone}
        </span>
      </div>
    ),
  },
  {
    id: 'status',
    header: '状态',
    enableSorting: false,
    cell: ({ row }) => {
      const s = row.original.status
      const variant =
        s === 'failed'
          ? 'destructive'
          : s === 'active'
            ? 'default'
            : s === 'done'
              ? 'secondary'
              : 'outline'
      return <Badge variant={variant}>{taskStatusLabel(s)}</Badge>
    },
  },
  {
    id: 'nextRun',
    header: '下次执行',
    enableSorting: false,
    cell: ({ row }) => <span className='text-sm'>{rel(row.original.nextRunAt)}</span>,
  },
  {
    id: 'lastRun',
    header: '上次运行',
    enableSorting: false,
    cell: ({ row }) => <span className='text-sm'>{rel(row.original.lastRunAt)}</span>,
  },
  {
    id: 'actions',
    header: '',
    enableSorting: false,
    cell: ({ row }) => <TaskRowActions task={row.original} />,
  },
]

function TaskRowActions({ task }: { task: TaskRow }) {
  const { setOpen, setCurrentTask } = useTasks()
  const cancellable = task.status === 'active' || task.status === 'paused'

  const fire = () => {
    api
      .fireTask(task.id)
      .then(() => toast.success('已触发一次执行'))
      .catch((e: unknown) =>
        toast.error(e instanceof ApiError ? e.message : '触发失败')
      )
  }

  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger asChild>
        <Button
          variant='ghost'
          className='h-8 w-8 p-0 data-[state=open]:bg-muted'
        >
          <MoreHorizontal className='size-4' />
          <span className='sr-only'>打开菜单</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align='end' className='w-40'>
        <DropdownMenuItem
          disabled={!cancellable}
          onClick={() => {
            setCurrentTask(task)
            setOpen('runs')
          }}
        >
          运行记录
        </DropdownMenuItem>
        <DropdownMenuItem disabled={!cancellable} onClick={fire}>
          立即执行
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem
          className='text-destructive focus:text-destructive'
          disabled={!cancellable}
          onClick={() => {
            setCurrentTask(task)
            setOpen('cancel')
          }}
        >
          取消任务
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
