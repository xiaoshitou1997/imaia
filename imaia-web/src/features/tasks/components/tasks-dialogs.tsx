import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { api, ApiError, type TaskRun, type TaskRow } from '@/lib/api'
import { ConfirmDialog } from '@/components/confirm-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Input } from '@/components/ui/input'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { runStatusLabel } from '../data/status'
import { useTasks } from './tasks-provider'

export function TasksDialogs() {
  const { open, setOpen, currentTask, setCurrentTask } = useTasks()
  const queryClient = useQueryClient()

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ['tasks'] })

  return (
    <>
      <CreateTaskDialog
        key='task-create'
        open={open === 'create'}
        onOpenChange={() => setOpen('create')}
      />

      {currentTask && (
        <>
          <RunsDialog
            key={'task-runs-' + currentTask.id}
            open={open === 'runs'}
            onOpenChange={() => {
              setOpen('runs')
            }}
            task={currentTask}
          />

          <ConfirmDialog
            key='task-cancel'
            destructive
            open={open === 'cancel'}
            onOpenChange={() => {
              setOpen('cancel')
              setTimeout(() => setCurrentTask(null), 500)
            }}
            handleConfirm={() => {
              api
                .cancelTask(currentTask.id)
                .then(() => {
                  toast.success('任务已取消')
                  invalidate()
                })
                .catch((e: unknown) =>
                  toast.error(e instanceof ApiError ? e.message : '取消失败')
                )
                .finally(() => {
                  setOpen(null)
                  setTimeout(() => setCurrentTask(null), 500)
                })
            }}
            className='max-w-md'
            title={'取消任务 #' + currentTask.id + '？'}
            desc={
              <>
                取消后任务不再执行，状态变为已取消。该操作不可撤销。
              </>
            }
            confirmText='取消任务'
          />
        </>
      )}
    </>
  )
}

const createSchema = z
  .object({
    conversationId: z.string().min(1, '请选择目标会话'),
    scheduleMode: z.enum(['once', 'cron']),
    fireAt: z.string(),
    cron: z.string(),
    content: z.string(),
    prompt: z.string(),
  })
  .superRefine((v, ctx) => {
    if (v.scheduleMode === 'once' && !v.fireAt) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['fireAt'],
        message: '请填写触发时间',
      })
    }
    if (v.scheduleMode === 'cron' && !v.cron.trim()) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['cron'],
        message: '请填写 cron 表达式',
      })
    }
    if (!v.content.trim() && !v.prompt.trim()) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['prompt'],
        message: 'content 与 prompt 至少填一个',
      })
    }
  })

type CreateTaskForm = z.infer<typeof createSchema>

function CreateTaskDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: () => void
}) {
  const queryClient = useQueryClient()
  const conversations = useQuery({
    queryKey: ['conversations', '', 1, 'select-options'],
    queryFn: () => api.conversations(undefined, 1, 100),
  })

  const form = useForm<CreateTaskForm>({
    resolver: zodResolver(createSchema),
    defaultValues: {
      conversationId: '',
      scheduleMode: 'once',
      fireAt: '',
      cron: '0 8 * * *',
      content: '',
      prompt: '',
    },
  })

  const mode = form.watch('scheduleMode')

  const onSubmit = form.handleSubmit((values) => {
    return api
      .createTask({
        conversationId: Number(values.conversationId),
        content: values.scheduleMode === 'once' ? values.content.trim() || null : null,
        prompt: values.prompt.trim() || null,
        fireAt:
          values.scheduleMode === 'once'
            ? values.fireAt.replace('T', ' ')
            : null,
        cron: values.scheduleMode === 'cron' ? values.cron.trim() : null,
      })
      .then(() => {
        toast.success('任务已创建')
        form.reset()
        onOpenChange()
        queryClient.invalidateQueries({ queryKey: ['tasks'] })
      })
      .catch((e: unknown) =>
        toast.error(e instanceof ApiError ? e.message : '创建失败')
      )
  })

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) onOpenChange() }}>
      <DialogContent className='sm:max-w-lg'>
        <DialogHeader>
          <DialogTitle>新建定时任务</DialogTitle>
          <DialogDescription>
            一次性（到点一次）与周期（cron）二选一；prompt 有则 AI 措辞，无则直发 content。
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className='space-y-4'>
            <FormField
              control={form.control}
              name='conversationId'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>目标会话</FormLabel>
                  <Select onValueChange={field.onChange} value={field.value}>
                    <FormControl>
                      <SelectTrigger className='w-full'>
                        <SelectValue placeholder='选择会话' />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {(conversations.data?.items ?? []).map((c) => (
                        <SelectItem key={c.id} value={String(c.id)}>
                          {c.displayName}（#{c.id}）
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='scheduleMode'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>调度方式</FormLabel>
                  <div className='flex gap-2'>
                    <Button
                      size='sm'
                      type='button'
                      variant={field.value === 'once' ? 'default' : 'outline'}
                      onClick={() => field.onChange('once')}
                    >
                      一次性
                    </Button>
                    <Button
                      size='sm'
                      type='button'
                      variant={field.value === 'cron' ? 'default' : 'outline'}
                      onClick={() => field.onChange('cron')}
                    >
                      周期
                    </Button>
                  </div>
                </FormItem>
              )}
            />
            {mode === 'once' ? (
              <FormField
                control={form.control}
                name='fireAt'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>触发时间</FormLabel>
                    <FormControl>
                      <Input type='datetime-local' {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : (
              <FormField
                control={form.control}
                name='cron'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>cron 表达式</FormLabel>
                    <FormControl>
                      <Input placeholder='0 8 * * *' {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            )}
            <FormField
              control={form.control}
              name='content'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>直发内容（可选）</FormLabel>
                  <FormControl>
                    <Input placeholder='原样发送的文本' {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='prompt'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>AI 措辞 prompt（可选）</FormLabel>
                  <FormControl>
                    <Input placeholder='描述要表达的意思，AI 负责措辞' {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <DialogFooter>
              <Button
                type='button'
                variant='outline'
                onClick={onOpenChange}
              >
                取消
              </Button>
              <Button type='submit' disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? (
                  <Loader2 className='animate-spin' />
                ) : null}
                创建
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}

function RunsDialog({
  open,
  onOpenChange,
  task,
}: {
  open: boolean
  onOpenChange: () => void
  task: TaskRow
}) {
  const [page, setPage] = useState(1)
  const runs = useQuery({
    queryKey: ['task-runs', task.id, page],
    queryFn: () => api.taskRuns(task.id, page, 10),
    enabled: open,
  })

  const rows = runs.data?.items ?? []
  const totalPages = runs.data?.totalPages ?? 1

  // 每次打开都从第一页看起：在打开动作里重置，而不是开一个 effect 去同步 setState。
  const handleOpenChange = (next: boolean) => {
    if (next) setPage(1)
    else onOpenChange()
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className='sm:max-w-2xl'>
        <DialogHeader>
          <DialogTitle>运行记录 · #{task.id}</DialogTitle>
          <DialogDescription>
            {task.conversationLabel ?? '未知会话'} 最近 10 条 / 页。
          </DialogDescription>
        </DialogHeader>
        <div className='overflow-hidden rounded-md border'>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>计划时间</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>尝试</TableHead>
                <TableHead>输出 / 错误</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.length ? (
                rows.map((r: TaskRun) => (
                  <TableRow key={r.id}>
                    <TableCell className='whitespace-nowrap text-xs'>
                      {r.startedAt
                        ? r.startedAt.replace('T', ' ').slice(0, 19)
                        : r.scheduledFor
                          ? r.scheduledFor.replace('T', ' ').slice(0, 19)
                          : '—'}
                    </TableCell>
                    <TableCell>
                      <Badge
                        variant={
                          r.status === 'failed' ? 'destructive' : r.status === 'success' ? 'secondary' : 'outline'
                        }
                      >
                        {runStatusLabel(r.status)}
                      </Badge>
                    </TableCell>
                    <TableCell className='tabular-nums'>{r.attempt}</TableCell>
                    <TableCell className='max-w-72'>
                      <span className='line-clamp-2 text-xs text-muted-foreground'>
                        {r.error || r.outputText || '—'}
                      </span>
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={4} className='h-20 text-center text-muted-foreground'>
                    {runs.isLoading ? '加载中…' : '还没有运行记录。'}
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>
        <DialogFooter className='items-center'>
          <span className='text-xs text-muted-foreground'>
            第 {page} / {totalPages} 页
          </span>
          <Button
            variant='outline'
            size='sm'
            disabled={page <= 1}
            onClick={() => setPage((p) => p - 1)}
          >
            上一页
          </Button>
          <Button
            variant='outline'
            size='sm'
            disabled={page >= totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            下一页
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
