import { useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { api, ApiError, type Settings } from '@/lib/api'
import { Separator } from '@/components/ui/separator'
import { Button } from '@/components/ui/button'
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'

const formSchema = z.object({
  enabled: z.boolean(),
  systemPrompt: z.string().max(8000, '提示词过长'),
  historyLimit: z
    .number('请输入数字')
    .int()
    .min(2, '范围 2-200')
    .max(200, '范围 2-200'),
  requireGroupMention: z.boolean(),
  quietHoursEnabled: z.boolean(),
  quietHoursStart: z.string(),
  quietHoursEnd: z.string(),
  timezone: z.string().min(1, '时区不能为空'),
})

type AssistantForm = z.infer<typeof formSchema>

export function AssistantSettings() {
  return (
    <div className='space-y-6 overflow-y-auto pb-8'>
      <div>
        <h3 className='text-lg font-medium'>助手</h3>
        <p className='text-sm text-muted-foreground'>
          回复总开关、默认提示词、上下文窗口与免打扰时段。
        </p>
      </div>
      <Separator />
      <AssistantForm />
    </div>
  )
}

function AssistantForm() {
  const queryClient = useQueryClient()
  const settings = useQuery({ queryKey: ['settings'], queryFn: api.getSettings })

  const form = useForm<AssistantForm>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      enabled: false,
      systemPrompt: '',
      historyLimit: 20,
      requireGroupMention: true,
      quietHoursEnabled: false,
      quietHoursStart: '23:00',
      quietHoursEnd: '08:00',
      timezone: 'Asia/Shanghai',
    },
  })

  useEffect(() => {
    const s = settings.data
    if (!s) return
    form.reset({
      enabled: s.enabled,
      systemPrompt: s.systemPrompt ?? '',
      historyLimit: s.historyLimit,
      requireGroupMention: s.requireGroupMention,
      quietHoursEnabled: s.quietHoursEnabled,
      quietHoursStart: s.quietHoursStart,
      quietHoursEnd: s.quietHoursEnd,
      timezone: s.timezone,
    })
  }, [settings.data, form])

  const onSubmit = form.handleSubmit((values) => {
    const body: Partial<Settings> = {
      enabled: values.enabled,
      systemPrompt: values.systemPrompt,
      historyLimit: values.historyLimit,
      requireGroupMention: values.requireGroupMention,
      quietHoursEnabled: values.quietHoursEnabled,
      quietHoursStart: values.quietHoursStart,
      quietHoursEnd: values.quietHoursEnd,
      timezone: values.timezone,
    }
    return api
      .updateSettings(body)
      .then((saved) => {
        queryClient.setQueryData(['settings'], saved)
        queryClient.invalidateQueries({ queryKey: ['overview'] })
        queryClient.invalidateQueries({ queryKey: ['runtime'] })
        toast.success('助手设置已保存')
        return null
      })
      .catch((e: unknown) =>
        toast.error(e instanceof ApiError ? e.message : '保存失败')
      )
  })

  const quietOn = form.watch('quietHoursEnabled')

  return (
    <Form {...form}>
      <form onSubmit={onSubmit} className='space-y-6'>
        <FormField
          control={form.control}
          name='enabled'
          render={({ field }) => (
            <FormItem className='flex items-center justify-between rounded-md border p-4'>
              <div className='space-y-0.5'>
                <FormLabel className='text-base'>AI 回复总开关</FormLabel>
                <FormDescription>
                  关闭后所有会话只归档消息，不触发 AI 回复。
                </FormDescription>
              </div>
              <FormControl>
                <Switch checked={field.value} onCheckedChange={field.onChange} />
              </FormControl>
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name='systemPrompt'
          render={({ field }) => (
            <FormItem>
              <FormLabel>默认提示词</FormLabel>
              <FormControl>
                <Textarea rows={6} placeholder='助手的人设与行为约定…' {...field} />
              </FormControl>
              <FormDescription>
                全局兜底；会话可在「会话」页设置自己的 AI 人设，优先生效。
              </FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />
        <div className='grid gap-4 sm:grid-cols-2'>
          <FormField
            control={form.control}
            name='historyLimit'
            render={({ field }) => (
              <FormItem>
                <FormLabel>历史窗口条数</FormLabel>
                <FormControl>
                  <Input
                    type='number'
                    min={2}
                    max={200}
                    {...field}
                    onChange={(e) => field.onChange(Number(e.target.value))}
                  />
                </FormControl>
                <FormDescription>每次回复携带的最近消息条数（2-200）。</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='timezone'
            render={({ field }) => (
              <FormItem>
                <FormLabel>时区</FormLabel>
                <FormControl>
                  <Input placeholder='Asia/Shanghai' {...field} />
                </FormControl>
                <FormDescription>IANA 时区；用于免打扰与任务调度。</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
        <FormField
          control={form.control}
          name='requireGroupMention'
          render={({ field }) => (
            <FormItem className='flex items-center justify-between rounded-md border p-4'>
              <div className='space-y-0.5'>
                <FormLabel>群聊需 @提及</FormLabel>
                <FormDescription>
                  群聊里只有 @助手的消息才触发回复，私聊不受影响。
                </FormDescription>
              </div>
              <FormControl>
                <Switch checked={field.value} onCheckedChange={field.onChange} />
              </FormControl>
            </FormItem>
          )}
        />
        <div className='space-y-4 rounded-md border p-4'>
          <FormField
            control={form.control}
            name='quietHoursEnabled'
            render={({ field }) => (
              <FormItem className='flex items-center justify-between'>
                <div className='space-y-0.5'>
                  <FormLabel>免打扰时段</FormLabel>
                  <FormDescription>时段内只归档不回复。</FormDescription>
                </div>
                <FormControl>
                  <Switch checked={field.value} onCheckedChange={field.onChange} />
                </FormControl>
              </FormItem>
            )}
          />
          {quietOn ? (
            <div className='grid gap-4 sm:grid-cols-2'>
              <FormField
                control={form.control}
                name='quietHoursStart'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>开始</FormLabel>
                    <FormControl>
                      <Input type='time' {...field} />
                    </FormControl>
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='quietHoursEnd'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>结束</FormLabel>
                    <FormControl>
                      <Input type='time' {...field} />
                    </FormControl>
                  </FormItem>
                )}
              />
            </div>
          ) : null}
        </div>
        <Button type='submit' disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting ? (
            <Loader2 className='animate-spin' />
          ) : null}
          保存
        </Button>
      </form>
    </Form>
  )
}
