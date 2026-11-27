import { useEffect } from 'react'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { api, ApiError, type Conversation } from '@/lib/api'
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
  title: z.string().max(100, '标题过长'),
  remark: z.string().max(200, '备注过长'),
  persona: z.string().max(2000, '人设过长'),
  autoreply: z.boolean(),
  selfCommandAlias: z.string().max(50, '别名过长'),
  selfCommandEnabled: z.boolean(),
})

interface EditConversationDialogProps {
  convo: Conversation | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function EditConversationDialog({
  convo,
  open,
  onOpenChange,
}: EditConversationDialogProps) {
  const queryClient = useQueryClient()

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      title: '',
      remark: '',
      persona: '',
      autoreply: false,
      selfCommandAlias: '',
      selfCommandEnabled: true,
    },
  })

  useEffect(() => {
    if (open && convo) {
      form.reset({
        title: convo.title ?? '',
        remark: convo.remark ?? '',
        persona: convo.persona ?? '',
        autoreply: convo.autoreply,
        selfCommandAlias: convo.selfCommandAlias ?? '',
        selfCommandEnabled: convo.selfCommandEnabled,
      })
    }
  }, [open, convo, form])

  const onSubmit = form.handleSubmit((values) => {
    if (!convo) return
    return api
      .updateConversation(convo.id, {
        title: values.title.trim(),
        remark: values.remark.trim(),
        persona: values.persona.trim(),
        autoreply: values.autoreply,
        selfCommandAlias: values.selfCommandAlias.trim(),
        selfCommandEnabled: values.selfCommandEnabled,
      })
      .then(() => {
        toast.success('会话已保存')
        queryClient.invalidateQueries({ queryKey: ['conversations'] })
        onOpenChange(false)
      })
      .catch((e: unknown) =>
        toast.error(e instanceof ApiError ? e.message : '保存失败')
      )
  })

  return (
    <Dialog open={open && convo !== null} onOpenChange={onOpenChange}>
      <DialogContent className='sm:max-w-lg'>
        <DialogHeader>
          <DialogTitle>编辑会话</DialogTitle>
          <DialogDescription>
            {convo
              ? convo.displayName +
                ' · 修改展示名、备注、AI 人设、自动回复与自指令。'
              : ''}
          </DialogDescription>
        </DialogHeader>
        {convo ? (
          <Form {...form}>
            <form onSubmit={onSubmit} className='space-y-4'>
              <FormField
                control={form.control}
                name='title'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>标题</FormLabel>
                    <FormControl>
                      <Input placeholder='会话展示名' {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='remark'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>备注</FormLabel>
                    <FormControl>
                      <Input placeholder='给自己看的备注' {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='persona'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>AI 人设（会话级）</FormLabel>
                    <FormControl>
                      <Textarea
                        rows={4}
                        placeholder='留空则使用全局 system prompt'
                        {...field}
                      />
                    </FormControl>
                    <FormDescription>
                      只对这个会话生效，优先于全局提示词。
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='autoreply'
                render={({ field }) => (
                  <FormItem className='flex items-center justify-between rounded-md border p-3'>
                    <div className='space-y-0.5'>
                      <FormLabel>自动回复</FormLabel>
                      <FormDescription>
                        关闭后此会话只归档，不触发 AI 回复。
                      </FormDescription>
                    </div>
                    <FormControl>
                      <Switch
                        checked={field.value}
                        onCheckedChange={field.onChange}
                      />
                    </FormControl>
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='selfCommandEnabled'
                render={({ field }) => (
                  <FormItem className='flex items-center justify-between rounded-md border p-3'>
                    <div className='space-y-0.5'>
                      <FormLabel>自指令</FormLabel>
                      <FormDescription>
                        关闭后本会话不响应「@别名 内容」，即使上层配了别名。
                      </FormDescription>
                    </div>
                    <FormControl>
                      <Switch
                        checked={field.value}
                        onCheckedChange={field.onChange}
                      />
                    </FormControl>
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='selfCommandAlias'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>自指令别名（会话级）</FormLabel>
                    <FormControl>
                      <Input placeholder='你在本群的昵称，如：小艾' {...field} />
                    </FormControl>
                    <FormDescription>
                      你自己手打「@别名 内容」即让 AI 在此会话回复（无需真实
                      @，手机端可用）。留空则沿用全局默认别名；要彻底关掉本会话，
                      请用上面的「自指令」开关。建议只对群聊开启。
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <DialogFooter>
                <Button
                  type='button'
                  variant='outline'
                  onClick={() => onOpenChange(false)}
                >
                  取消
                </Button>
                <Button type='submit' disabled={form.formState.isSubmitting}>
                  {form.formState.isSubmitting ? (
                    <Loader2 className='animate-spin' />
                  ) : null}
                  保存
                </Button>
              </DialogFooter>
            </form>
          </Form>
        ) : null}
      </DialogContent>
    </Dialog>
  )
}
