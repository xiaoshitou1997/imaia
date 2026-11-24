import { useEffect } from 'react'
import { z } from 'zod'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { api, ApiError, type Account } from '@/lib/api'
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
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { NumberInput, ToggleInput } from './account-fields'

const DEFAULT_WECHAT = {
  baseUrl: 'http://127.0.0.1:6174',
  token: '',
  account: '',
  consumerName: 'imaia',
  connectTimeoutMillis: '5000',
  callTimeoutSeconds: '15',
  reconnectDelayMillis: '2000',
  retryInitialBackoffMillis: '1000',
  retryMaxBackoffMillis: '30000',
  ackEnabled: true,
  exitOnFailure: true,
}

const DEFAULT_FEISHU = {
  appId: '',
  appSecret: '',
  domain: 'https://open.feishu.cn',
  exitOnFailure: true,
}

const connectionSchema = z.object({
  platformCode: z.enum(['wechat', 'feishu']),
  name: z.string().min(1, '名称不能为空'),
  wechat: z.object({
    baseUrl: z.string().min(1, '微信 Base URL 不能为空'),
    token: z.string(),
    account: z.string(),
    consumerName: z.string(),
    connectTimeoutMillis: z.string(),
    callTimeoutSeconds: z.string(),
    reconnectDelayMillis: z.string(),
    retryInitialBackoffMillis: z.string(),
    retryMaxBackoffMillis: z.string(),
    ackEnabled: z.boolean(),
    exitOnFailure: z.boolean(),
  }),
  feishu: z.object({
    appId: z.string(),
    appSecret: z.string(),
    domain: z.string().min(1, '飞书域名不能为空'),
    exitOnFailure: z.boolean(),
  }),
})

type ConnectionForm = z.infer<typeof connectionSchema>

const parseConfig = (json: string): Record<string, unknown> => {
  try {
    const parsed = JSON.parse(json) as unknown
    return parsed && typeof parsed === 'object'
      ? (parsed as Record<string, unknown>)
      : {}
  } catch {
    return {}
  }
}

const text = (v: unknown, fallback = '') =>
  typeof v === 'string' && v ? v : fallback

const num = (v: unknown, fallback: string) =>
  typeof v === 'number' ? String(v) : fallback

const bool = (v: unknown, fallback: boolean) =>
  typeof v === 'boolean' ? v : fallback

const asNumber = (v: string, fallback: number) => {
  const parsed = Number(v)
  return Number.isFinite(parsed) ? parsed : fallback
}

interface AccountConnectionDialogProps {
  account: Account | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function AccountConnectionDialog({
  account,
  open,
  onOpenChange,
}: AccountConnectionDialogProps) {
  const queryClient = useQueryClient()

  const form = useForm<ConnectionForm>({
    resolver: zodResolver(connectionSchema),
    defaultValues: {
      platformCode: 'wechat',
      name: '',
      wechat: DEFAULT_WECHAT,
      feishu: DEFAULT_FEISHU,
    },
  })

  useEffect(() => {
    if (!open) return
    const platformCode = account?.platformCode === 'feishu' ? 'feishu' : 'wechat'
    const config = parseConfig(account?.config ?? '{}')
    form.reset({
      platformCode,
      name: account?.name ?? '',
      wechat: {
        baseUrl: text(config.baseUrl, DEFAULT_WECHAT.baseUrl),
        token: text(config.token),
        account: text(config.account),
        consumerName: text(config.consumerName, DEFAULT_WECHAT.consumerName),
        connectTimeoutMillis: num(config.connectTimeoutMillis, DEFAULT_WECHAT.connectTimeoutMillis),
        callTimeoutSeconds: num(config.callTimeoutSeconds, DEFAULT_WECHAT.callTimeoutSeconds),
        reconnectDelayMillis: num(config.reconnectDelayMillis, DEFAULT_WECHAT.reconnectDelayMillis),
        retryInitialBackoffMillis: num(config.retryInitialBackoffMillis, DEFAULT_WECHAT.retryInitialBackoffMillis),
        retryMaxBackoffMillis: num(config.retryMaxBackoffMillis, DEFAULT_WECHAT.retryMaxBackoffMillis),
        ackEnabled: bool(config.ackEnabled, true),
        exitOnFailure: bool(config.exitOnFailure, true),
      },
      feishu: {
        appId: text(config.appId),
        appSecret: text(config.appSecret),
        domain: text(config.domain, DEFAULT_FEISHU.domain),
        exitOnFailure: bool(config.exitOnFailure, true),
      },
    })
  }, [open, account, form])

  const onSubmit = form.handleSubmit((values) => {
    const config =
      values.platformCode === 'wechat'
        ? {
            baseUrl: values.wechat.baseUrl.trim(),
            token: values.wechat.token,
            account: values.wechat.account.trim(),
            consumerName: values.wechat.consumerName.trim() || 'imaia',
            connectTimeoutMillis: asNumber(values.wechat.connectTimeoutMillis, 5000),
            callTimeoutSeconds: asNumber(values.wechat.callTimeoutSeconds, 15),
            reconnectDelayMillis: asNumber(values.wechat.reconnectDelayMillis, 2000),
            retryInitialBackoffMillis: asNumber(values.wechat.retryInitialBackoffMillis, 1000),
            retryMaxBackoffMillis: asNumber(values.wechat.retryMaxBackoffMillis, 30000),
            ackEnabled: values.wechat.ackEnabled,
            exitOnFailure: values.wechat.exitOnFailure,
          }
        : {
            appId: values.feishu.appId.trim(),
            appSecret: values.feishu.appSecret,
            domain: values.feishu.domain.trim() || DEFAULT_FEISHU.domain,
            exitOnFailure: values.feishu.exitOnFailure,
          }

    const request = account
      ? api.updateAccount(account.id, { name: values.name.trim(), config })
      : api.createAccount({
          platformCode: values.platformCode,
          name: values.name.trim(),
          enabled: false,
          config,
        })

    return request
      .then(() => {
        toast.success(
          account
            ? '账号已保存，连接配置即时生效。保留掩码的密钥未改动。'
            : '账号已创建（默认停用）。填好凭据后打开启用开关，消息流自动连接。'
        )
        queryClient.invalidateQueries({ queryKey: ['accounts'] })
        queryClient.invalidateQueries({ queryKey: ['runtime'] })
        onOpenChange(false)
      })
      .catch((e: unknown) =>
        toast.error(e instanceof ApiError ? e.message : '保存失败')
      )
  })

  const platformCode = form.watch('platformCode')

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className='max-h-svh overflow-y-auto sm:max-w-xl'>
        <DialogHeader>
          <DialogTitle>{account ? '编辑账号' : '新建账号'}</DialogTitle>
          <DialogDescription>
            {account
              ? account.platformCode + ' · ' + account.name + ' · 凭据为掩码显示。'
              : '创建后默认停用；凭据填好并启用后，消息流自动连接。'}
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className='space-y-4'>
            <div className='grid gap-4 sm:grid-cols-2'>
              <FormField
                control={form.control}
                name='platformCode'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>平台</FormLabel>
                    <Select
                      onValueChange={field.onChange}
                      value={field.value}
                      disabled={Boolean(account)}
                    >
                      <FormControl>
                        <SelectTrigger className='w-full'>
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        <SelectItem value='wechat'>微信（daemon 桥接）</SelectItem>
                        <SelectItem value='feishu'>飞书（开放平台）</SelectItem>
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='name'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>名称</FormLabel>
                    <FormControl>
                      <Input placeholder='default' {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <Separator />

            {platformCode === 'wechat' ? (
              <Controller
                control={form.control}
                name='wechat'
                render={({ field }) => (
                  <div className='space-y-4'>
                    <div className='grid gap-4 sm:grid-cols-2'>
                      <FormItem>
                        <FormLabel>Base URL</FormLabel>
                        <FormControl>
                          <Input
                            value={field.value.baseUrl}
                            onChange={(e) =>
                              field.onChange({
                                ...field.value,
                                baseUrl: e.target.value,
                              })
                            }
                            placeholder={DEFAULT_WECHAT.baseUrl}
                          />
                        </FormControl>
                      </FormItem>
                      <FormItem>
                        <FormLabel>Token</FormLabel>
                        <FormControl>
                          <Input
                            value={field.value.token}
                            onChange={(e) =>
                              field.onChange({
                                ...field.value,
                                token: e.target.value,
                              })
                            }
                            placeholder='daemon token'
                          />
                        </FormControl>
                      </FormItem>
                      <FormItem>
                        <FormLabel>账号（可选）</FormLabel>
                        <Input
                          value={field.value.account}
                          onChange={(e) =>
                            field.onChange({
                              ...field.value,
                              account: e.target.value,
                            })
                          }
                          placeholder='wxid，留空取默认'
                        />
                      </FormItem>
                      <FormItem>
                        <FormLabel>消费者名</FormLabel>
                        <Input
                          value={field.value.consumerName}
                          onChange={(e) =>
                            field.onChange({
                              ...field.value,
                              consumerName: e.target.value,
                            })
                          }
                          placeholder='imaia'
                        />
                      </FormItem>
                    </div>
                    <div className='grid gap-4 sm:grid-cols-3'>
                      <NumberInput
                        label='连接超时（ms）'
                        value={field.value.connectTimeoutMillis}
                        onChange={(v) =>
                          field.onChange({ ...field.value, connectTimeoutMillis: v })
                        }
                      />
                      <NumberInput
                        label='调用超时（s）'
                        value={field.value.callTimeoutSeconds}
                        onChange={(v) =>
                          field.onChange({ ...field.value, callTimeoutSeconds: v })
                        }
                      />
                      <NumberInput
                        label='重连间隔（ms）'
                        value={field.value.reconnectDelayMillis}
                        onChange={(v) =>
                          field.onChange({ ...field.value, reconnectDelayMillis: v })
                        }
                      />
                      <NumberInput
                        label='重试起始退避（ms）'
                        value={field.value.retryInitialBackoffMillis}
                        onChange={(v) =>
                          field.onChange({ ...field.value, retryInitialBackoffMillis: v })
                        }
                      />
                      <NumberInput
                        label='重试最大退避（ms）'
                        value={field.value.retryMaxBackoffMillis}
                        onChange={(v) =>
                          field.onChange({ ...field.value, retryMaxBackoffMillis: v })
                        }
                      />
                    </div>
                    <div className='grid gap-2 sm:grid-cols-2'>
                      <ToggleInput
                        label='ACK 确认'
                        checked={field.value.ackEnabled}
                        onChange={(v) => field.onChange({ ...field.value, ackEnabled: v })}
                      />
                      <ToggleInput
                        label='失败即退出'
                        checked={field.value.exitOnFailure}
                        onChange={(v) => field.onChange({ ...field.value, exitOnFailure: v })}
                      />
                    </div>
                  </div>
                )}
              />
            ) : (
              <Controller
                control={form.control}
                name='feishu'
                render={({ field }) => (
                  <div className='space-y-4'>
                    <div className='grid gap-4 sm:grid-cols-2'>
                      <FormItem>
                        <FormLabel>App ID</FormLabel>
                        <Input
                          value={field.value.appId}
                          onChange={(e) =>
                            field.onChange({ ...field.value, appId: e.target.value })
                          }
                          placeholder='cli_xxx'
                        />
                      </FormItem>
                      <FormItem>
                        <FormLabel>App Secret</FormLabel>
                        <Input
                          value={field.value.appSecret}
                          onChange={(e) =>
                            field.onChange({ ...field.value, appSecret: e.target.value })
                          }
                          placeholder='密钥，掩码显示'
                        />
                      </FormItem>
                    </div>
                    <FormItem>
                      <FormLabel>域名</FormLabel>
                      <Input
                        value={field.value.domain}
                        onChange={(e) =>
                          field.onChange({ ...field.value, domain: e.target.value })
                        }
                        placeholder={DEFAULT_FEISHU.domain}
                      />
                    </FormItem>
                    <ToggleInput
                      label='失败即退出'
                      checked={field.value.exitOnFailure}
                      onChange={(v) => field.onChange({ ...field.value, exitOnFailure: v })}
                    />
                  </div>
                )}
              />
            )}

            <DialogFooter>
              <Button type='button' variant='outline' onClick={() => onOpenChange(false)}>
                取消
              </Button>
              <Button type='submit' disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? (
                  <Loader2 className='animate-spin' />
                ) : null}
                {account ? '保存' : '创建'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
