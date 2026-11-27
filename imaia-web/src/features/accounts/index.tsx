import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { formatDistanceToNow } from 'date-fns'
import { zhCN } from 'date-fns/locale'
import { MoreHorizontal, Pencil, Plug, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { api, ApiError, type Account } from '@/lib/api'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
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
import { AccountsDialogs } from './components/accounts-dialogs'
import { AccountsProvider, useAccounts } from './components/accounts-provider'

export function Accounts() {
  return (
    <AccountsProvider>
      <Header fixed>
        <div className='ms-auto flex items-center gap-2'>
          <ThemeSwitch />
          <ProfileDropdown />
        </div>
      </Header>

      <Main className='flex flex-1 flex-col gap-4 sm:gap-6'>
        <AccountsHeader />
        <AccountsBody />
      </Main>

      <AccountsDialogs />
    </AccountsProvider>
  )
}

function AccountsHeader() {
  const { setOpen } = useAccounts()
  return (
    <div className='flex flex-wrap items-end justify-between gap-2'>
      <div>
        <h2 className='text-2xl font-bold tracking-tight'>平台账号</h2>
        <p className='text-muted-foreground'>
          微信 / 飞书连接凭据。启用后消息流自动连接，变更即时生效。
        </p>
      </div>
      <Button className='space-x-1' onClick={() => setOpen('create')}>
        <span>新建账号</span> <Plus size={18} />
      </Button>
    </div>
  )
}

function AccountsBody() {
  const accounts = useQuery({
    queryKey: ['accounts'],
    queryFn: api.accounts,
  })
  const { setOpen, setCurrentAccount } = useAccounts()
  const [toggling, setToggling] = useState<number | null>(null)

  const rows = accounts.data ?? []

  const toggle = (account: Account, enabled: boolean) => {
    setToggling(account.id)
    api
      .updateAccount(account.id, { enabled })
      .then(() => {
        toast.success(
          enabled ? '已启用，消息流自动连接。' : '已停用，消息流已断开。'
        )
        return null
      })
      .catch((e: unknown) =>
        toast.error(e instanceof ApiError ? e.message : '操作失败')
      )
      .finally(() => setToggling(null))
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>账号列表</CardTitle>
        <CardDescription>
          密钥字段只显示掩码；保存时保留掩码即不改动原值。
        </CardDescription>
      </CardHeader>
      <CardContent className='space-y-1'>
        {accounts.isError ? (
          <div className='flex flex-col items-center gap-2 py-6 text-center'>
            <p className='text-sm text-muted-foreground'>加载失败，请稍后重试。</p>
            <Button variant='outline' size='sm' onClick={() => void accounts.refetch()}>
              重试
            </Button>
          </div>
        ) : accounts.isLoading ? (
          <p className='py-6 text-center text-sm text-muted-foreground'>
            加载中…
          </p>
        ) : rows.length ? (
          rows.map((a) => (
            <div
              key={a.id}
              className='flex items-center gap-3 rounded-md border-b py-3 last:border-b-0'
            >
              <Avatar className='size-9'>
                <AvatarFallback className='uppercase'>
                  {a.platformCode.slice(0, 1)}
                </AvatarFallback>
              </Avatar>
              <button
                type='button'
                className='min-w-0 flex-1 text-start'
                onClick={() => {
                  setCurrentAccount(a)
                  setOpen('edit')
                }}
              >
                <span className='block truncate text-sm font-medium'>
                  {a.name}
                </span>
                <span className='block truncate text-xs text-muted-foreground'>
                  {a.platformCode} · 更新于{' '}
                  {formatDistanceToNow(new Date(a.updatedAt), {
                    addSuffix: true,
                    locale: zhCN,
                  })}
                </span>
              </button>
              <div className='flex items-center gap-1'>
                <Switch
                  checked={a.enabled}
                  disabled={toggling === a.id}
                  onCheckedChange={(v) => toggle(a, v)}
                  aria-label='启用账号'
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
                  <DropdownMenuContent align='end' className='w-36'>
                    <DropdownMenuItem
                      onClick={() => {
                        setCurrentAccount(a)
                        setOpen('edit')
                      }}
                    >
                      <Pencil className='size-4' /> 编辑
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem
                      className='text-destructive focus:text-destructive'
                      onClick={() => {
                        setCurrentAccount(a)
                        setOpen('delete')
                      }}
                    >
                      <Trash2 className='size-4' /> 删除
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </div>
            </div>
          ))
        ) : (
          <div className='flex flex-col items-center gap-2 py-10 text-muted-foreground'>
            <Plug className='size-8' />
            <p className='text-sm'>
              还没有平台账号。新建一个并填好凭据，启用后消息流自动连接。
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
