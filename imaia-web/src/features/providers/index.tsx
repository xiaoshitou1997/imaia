import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { formatDistanceToNow } from 'date-fns'
import { zhCN } from 'date-fns/locale'
import { MoreHorizontal, Pencil, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { api, ApiError, type Provider } from '@/lib/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { ProfileDropdown } from '@/components/profile-dropdown'
import { ThemeSwitch } from '@/components/theme-switch'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Switch } from '@/components/ui/switch'
import { ProvidersDialogs } from './components/providers-dialogs'
import { ProvidersProvider, useProviders } from './components/providers-provider'

export function Providers() {
  return (
    <ProvidersProvider>
      <Header fixed>
        <div className='ms-auto flex items-center gap-2'>
          <ThemeSwitch />
          <ProfileDropdown />
        </div>
      </Header>

      <Main className='flex flex-1 flex-col gap-4 sm:gap-6'>
        <ProvidersHeader />
        <ProvidersBody />
      </Main>

      <ProvidersDialogs />
    </ProvidersProvider>
  )
}

function ProvidersHeader() {
  const { setOpen } = useProviders()
  return (
    <div className='flex flex-wrap items-end justify-between gap-2'>
      <div>
        <h2 className='text-2xl font-bold tracking-tight'>供应商</h2>
        <p className='text-muted-foreground'>
          接入 OpenAI 兼容的聊天服务；保存后自动拉取 /v1/models 建立模型目录。
        </p>
      </div>
      <Button className='space-x-1' onClick={() => setOpen('create')}>
        <span>新建供应商</span> <Plus size={18} />
      </Button>
    </div>
  )
}

function ProvidersBody() {
  const providers = useQuery({
    queryKey: ['providers'],
    queryFn: api.providers,
  })
  const { setOpen, setCurrentProvider } = useProviders()
  const [toggling, setToggling] = useState<number | null>(null)

  const rows = providers.data ?? []

  const toggle = (provider: Provider, enabled: boolean) => {
    setToggling(provider.id)
    api
      .updateProvider(provider.id, { enabled })
      .then(() => {
        toast.success(enabled ? '供应商已启用。' : '供应商已停用。')
        return null
      })
      .catch((e: unknown) =>
        toast.error(e instanceof ApiError ? e.message : '操作失败')
      )
      .finally(() => {
        setToggling(null)
        // 重新拉取以拿到后端归一化后的行（含槽位推导变化）
        providers.refetch()
      })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>供应商列表</CardTitle>
        <CardDescription>
          API Key 只显示掩码；保留掩码保存即不改动原值。启用的供应商才参与聊天槽位推导。
        </CardDescription>
      </CardHeader>
      <CardContent className='space-y-1'>
        {providers.isLoading ? (
          <p className='py-6 text-center text-sm text-muted-foreground'>
            加载中…
          </p>
        ) : rows.length ? (
          rows.map((p) => {
            const enabledModels = p.models.filter((m) => m.enabled).length
            const syncedAt = p.models[0]?.syncedAt
            return (
              <div
                key={p.id}
                className='flex items-center gap-3 rounded-md border-b py-3 last:border-b-0'
              >
                <button
                  type='button'
                  className='min-w-0 flex-1 text-start'
                  onClick={() => {
                    setCurrentProvider(p)
                    setOpen('models')
                  }}
                >
                  <span className='flex items-center gap-2'>
                    <span className='truncate text-sm font-medium'>{p.name}</span>
                    <Badge variant={enabledModels ? 'default' : 'outline'}>
                      {enabledModels} / {p.models.length} 模型启用
                    </Badge>
                  </span>
                  <span className='block truncate text-xs text-muted-foreground'>
                    {p.baseUrl}
                    {syncedAt
                      ? ' · 同步于 ' +
                        formatDistanceToNow(new Date(syncedAt), {
                          addSuffix: true,
                          locale: zhCN,
                        })
                      : p.sync
                        ? ' · ' + p.sync
                        : ' · 未同步'}
                  </span>
                </button>
                <div className='flex items-center gap-1'>
                  <Switch
                    checked={p.enabled}
                    disabled={toggling === p.id}
                    onCheckedChange={(v) => toggle(p, v)}
                    aria-label='启用供应商'
                  />
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
                        onClick={() => {
                          setCurrentProvider(p)
                          setOpen('models')
                        }}
                      >
                        管理模型
                      </DropdownMenuItem>
                      <DropdownMenuItem
                        onClick={() => {
                          setCurrentProvider(p)
                          setOpen('edit')
                        }}
                      >
                        <Pencil className='size-4' /> 编辑
                      </DropdownMenuItem>
                      <DropdownMenuSeparator />
                      <DropdownMenuItem
                        className='text-destructive focus:text-destructive'
                        onClick={() => {
                          setCurrentProvider(p)
                          setOpen('delete')
                        }}
                      >
                        <Trash2 className='size-4' /> 删除
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>
              </div>
            )
          })
        ) : (
          <div className='py-10 text-center'>
            <p className='text-sm text-muted-foreground'>
              还没有供应商。新建一个 OpenAI 兼容端点，保存后自动同步模型目录。
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
