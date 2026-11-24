import { useEffect, useState } from 'react'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { api, ApiError } from '@/lib/api'
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

const formSchema = z.object({
  name: z.string().min(1, '名称不能为空'),
  baseUrl: z
    .string()
    .min(1, 'Base URL 不能为空')
    .refine((v) => v.startsWith('http'), 'Base URL 需以 http 开头'),
  apiKey: z.string(),
  modelsDevId: z.string(),
})

type ProviderForm = z.infer<typeof formSchema>

interface ProviderDialogProps {
  open: boolean
  /** null = 新建 */
  provider: { id: number; name: string; baseUrl: string; apiKey: string; modelsDevId: string | null } | null
  onOpenChange: (open: boolean) => void
}

export function ProviderDialog({ open, provider, onOpenChange }: ProviderDialogProps) {
  const queryClient = useQueryClient()
  const [saving, setSaving] = useState(false)

  const form = useForm<ProviderForm>({
    resolver: zodResolver(formSchema),
    defaultValues: { name: '', baseUrl: '', apiKey: '', modelsDevId: '' },
  })

  useEffect(() => {
    if (!open) return
    form.reset({
      name: provider?.name ?? '',
      baseUrl: provider?.baseUrl ?? '',
      apiKey: provider?.apiKey ?? '',
      modelsDevId: provider?.modelsDevId ?? '',
    })
  }, [open, provider, form])

  const onSubmit = form.handleSubmit((values) => {
    setSaving(true)
    const request = provider
      ? api
          .updateProvider(provider.id, {
            baseUrl: values.baseUrl.trim(),
            apiKey: values.apiKey || undefined,
            modelsDevId: values.modelsDevId.trim() || null,
          })
          .then((updated) => {
            toast.success(updated.sync ? '已保存：' + updated.sync : '已保存')
            return null
          })
      : api
          .createProvider({
            name: values.name.trim(),
            baseUrl: values.baseUrl.trim(),
            apiKey: values.apiKey || undefined,
            modelsDevId: values.modelsDevId.trim() || undefined,
          })
          .then(({ sync }) => {
            toast.success('已创建：' + sync)
            return null
          })

    return request
      .catch((e: unknown) =>
        toast.error(e instanceof ApiError ? e.message : '保存失败')
      )
      .finally(() => {
        setSaving(false)
        queryClient.invalidateQueries({ queryKey: ['providers'] })
        queryClient.invalidateQueries({ queryKey: ['runtime'] })
        onOpenChange(false)
      })
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className='sm:max-w-lg'>
        <DialogHeader>
          <DialogTitle>{provider ? '编辑供应商' : '新建供应商'}</DialogTitle>
          <DialogDescription>
            {provider
              ? provider.name + ' · API Key 为掩码，保留掩码即不改动。'
              : '保存成功后会立即拉取 /v1/models 同步模型目录。'}
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className='space-y-4'>
            <FormField
              control={form.control}
              name='name'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>名称</FormLabel>
                  <FormControl>
                    <Input
                      placeholder='例如 sub2api'
                      {...field}
                      disabled={Boolean(provider)}
                    />
                  </FormControl>
                  <FormDescription>创建后不可改（模型按名称归属）。</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='baseUrl'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Base URL</FormLabel>
                  <FormControl>
                    <Input placeholder='https://api.example.com/v1' {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='apiKey'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>API Key{provider ? '（可选）' : ''}</FormLabel>
                  <FormControl>
                    <Input
                      type='password'
                      placeholder={provider ? '保留掩码即不改动' : 'sk-…'}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='modelsDevId'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>models.dev ID（可选）</FormLabel>
                  <FormControl>
                    <Input placeholder='例如 anthropic' {...field} />
                  </FormControl>
                  <FormDescription>
                    用于补充模型上下文窗口与价格等元数据；留空只用 /v1/models。
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <DialogFooter>
              <Button type='button' variant='outline' onClick={() => onOpenChange(false)}>
                取消
              </Button>
              <Button type='submit' disabled={saving}>
                {saving ? <Loader2 className='animate-spin' /> : null}
                {provider ? '保存' : '创建'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
