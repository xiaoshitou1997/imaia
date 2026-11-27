import { useEffect, useMemo } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { api, ApiError, type Settings } from '@/lib/api'
import { QueryState } from '@/components/query-state'
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

  // The schema shape is fixed for this page's field set, so it can be spelled out
  // once as a literal. A Record<string, ZodTypeAny> shape used to force every call
  // site through `as never`, which severed the link to the Settings contract.
  const formSchema = useMemo(
    () =>
      z.object(
        Object.fromEntries(
          fields.map((f) => [f.key, z.string()])
        ) as Record<ToolFieldConfig['key'], z.ZodString>
      ),
    [fields]
  )
  type ToolFormValues = z.infer<typeof formSchema>

  const form = useForm<ToolFormValues>({
    resolver: zodResolver(formSchema),
    mode: 'onTouched',
    defaultValues: Object.fromEntries(
      fields.map((f) => [f.key, ''])
    ) as ToolFormValues,
  })

  useEffect(() => {
    const s = settings.data
    if (!s) return
    form.reset(
      Object.fromEntries(
        fields.map((f) => [f.key, s[f.key] ?? ''])
      ) as ToolFormValues
    )
  }, [settings.data, form, fields])

  const onSubmit = form.handleSubmit((values) => {
    // Differencing against the loaded settings is what keeps a failed GET from
    // overwriting real keys: an unloaded form holds '' for every secret field, and
    // '' has "clear this value" semantics in SettingsController.applyToolKey.
    const body: Partial<Settings> = {}
    for (const f of fields) {
      if (settings.data && settings.data[f.key] !== values[f.key]) {
        Object.assign(body, { [f.key]: values[f.key] })
      }
    }
    if (Object.keys(body).length === 0) {
      toast.info('没有需要保存的修改')
      return Promise.resolve(null)
    }
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

  const isReady = !settings.isError && !!settings.data

  return (
    <QueryState
      isLoading={settings.isLoading}
      isError={settings.isError}
      errorText='加载工具端点配置失败。为避免用默认值覆盖服务器上的真实配置，保存已禁用。'
      onRetry={() => void settings.refetch()}
    >
      <Form {...form}>
        <form onSubmit={onSubmit} className='space-y-6'>
          {fields.map((f) => (
            <FormField
              key={f.key}
              control={form.control}
              name={f.key}
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
          <Button
            type='submit'
            disabled={!isReady || form.formState.isSubmitting}
          >
            {form.formState.isSubmitting ? (
              <Loader2 className='animate-spin' />
            ) : null}
            保存
          </Button>
        </form>
      </Form>
    </QueryState>
  )
}
