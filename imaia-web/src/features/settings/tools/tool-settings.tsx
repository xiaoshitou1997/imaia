import { useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { api, ApiError, type Settings } from '@/lib/api'
import { Badge } from '@/components/ui/badge'
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

/**
 * 工具端点表单：图像 / 视频 / 搜索三个子页同构。
 * 工具键独立于供应商（globalOnly），保存只提交本页字段——后端 PUT 为部分更新。
 */
export interface ToolFieldConfig {
  key: 'imageBaseUrl' | 'imageApiKey' | 'imageModel' | 'videoBaseUrl' | 'videoApiKey' | 'videoModel' | 'webSearchBaseUrl' | 'webSearchApiKey'
  label: string
  placeholder?: string
  password?: boolean
  description?: string
}

export interface ToolSettingsProps {
  title: string
  description: string
  /** runtime 能力键，用于显示就绪徽标。 */
  capability: 'images' | 'video' | 'search'
  fields: ToolFieldConfig[]
}

export function ToolSettings({
  title,
  description,
  capability,
  fields,
}: ToolSettingsProps) {
  return (
    <div className='space-y-6 overflow-y-auto pb-8'>
      <div>
        <h3 className='flex items-center gap-2 text-lg font-medium'>
          {title}
          <ReadyBadge capability={capability} />
        </h3>
        <p className='text-sm text-muted-foreground'>{description}</p>
      </div>
      <Separator />
      <ToolForm fields={fields} />
    </div>
  )
}

function ReadyBadge({ capability }: { capability: ToolSettingsProps['capability'] }) {
  const runtime = useQuery({ queryKey: ['runtime'], queryFn: api.runtime })
  const ok = runtime.data?.[capability] ?? false
  return (
    <Badge variant={ok ? 'default' : 'outline'}>
      {ok ? '就绪' : '未配置'}
    </Badge>
  )
}

function ToolForm({ fields }: { fields: ToolFieldConfig[] }) {
  const queryClient = useQueryClient()
  const settings = useQuery({ queryKey: ['settings'], queryFn: api.getSettings })

  const shape: Record<string, z.ZodTypeAny> = {}
  for (const f of fields) {
    shape[f.key] = z.string()
  }
  const formSchema = z.object(shape)
  type ToolForm = z.infer<typeof formSchema>

  const empty: Record<string, string> = {}
  for (const f of fields) empty[f.key] = ''

  const form = useForm<ToolForm>({
    resolver: zodResolver(formSchema as never),
    defaultValues: empty as never,
  })

  useEffect(() => {
    const s = settings.data
    if (!s) return
    const values: Record<string, string> = {}
    for (const f of fields) {
      values[f.key] = (s as unknown as Record<string, unknown>)[f.key] as string
    }
    form.reset(values as never)
  }, [settings.data, form, fields])

  const onSubmit = form.handleSubmit((values) => {
    const body = values as Partial<Settings>
    return api
      .updateSettings(body)
      .then((saved) => {
        queryClient.setQueryData(['settings'], saved)
        queryClient.invalidateQueries({ queryKey: ['runtime'] })
        toast.success('已保存')
        return null
      })
      .catch((e: unknown) =>
        toast.error(e instanceof ApiError ? e.message : '保存失败')
      )
  })

  return (
    <Form {...form}>
      <form onSubmit={onSubmit} className='space-y-6'>
        {fields.map((f) => (
          <FormField
            key={f.key}
            control={form.control}
            name={f.key as never}
            render={({ field }) => (
              <FormItem>
                <FormLabel>{f.label}</FormLabel>
                <FormControl>
                  <Input
                    type={f.password ? 'password' : 'text'}
                    placeholder={f.placeholder}
                    {...field}
                  />
                </FormControl>
                {f.description ? (
                  <FormDescription>{f.description}</FormDescription>
                ) : null}
                <FormMessage />
              </FormItem>
            )}
          />
        ))}
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
