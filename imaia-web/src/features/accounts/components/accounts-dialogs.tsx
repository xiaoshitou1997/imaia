import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api, ApiError } from '@/lib/api'
import { ConfirmDialog } from '@/components/confirm-dialog'
import { Input } from '@/components/ui/input'
import { AccountConnectionDialog } from './account-connection-dialog'
import { useAccounts } from './accounts-provider'

export function AccountsDialogs() {
  const { open, setOpen, currentAccount, setCurrentAccount } = useAccounts()
  const queryClient = useQueryClient()
  const [deleting, setDeleting] = useState(false)

  const closeRowDialogs = () => {
    setOpen(null)
    setTimeout(() => setCurrentAccount(null), 500)
  }

  const remove = () => {
    if (!currentAccount) return
    setDeleting(true)
    api
      .deleteAccount(currentAccount.id)
      .then(() => {
        toast.success('账号已删除')
        queryClient.invalidateQueries({ queryKey: ['accounts'] })
        queryClient.invalidateQueries({ queryKey: ['runtime'] })
        closeRowDialogs()
      })
      .catch((e: unknown) =>
        toast.error(e instanceof ApiError ? e.message : '删除失败')
      )
      .finally(() => setDeleting(false))
  }

  return (
    <>
      <AccountConnectionDialog
        key={'account-edit-' + (currentAccount?.id ?? 'create')}
        account={open === 'edit' ? currentAccount : null}
        open={open === 'create' || open === 'edit'}
        onOpenChange={(o) => {
          if (!o) closeRowDialogs()
        }}
      />

      {currentAccount && open === 'delete' ? (
        <DeleteAccountConfirm account={currentAccount} onRemove={remove} deleting={deleting}
          onClose={closeRowDialogs} />
      ) : null}
    </>
  )
}

/**
 * 删除确认输入独立成组件：仅在删除对话框打开时挂载，输入值随重挂自然清空，
 * 不需要在 effect 里同步 setState（级联渲染）。
 */
function DeleteAccountConfirm({ account, onRemove, deleting, onClose }: {
  account: NonNullable<ReturnType<typeof useAccounts>['currentAccount']>
  onRemove: () => void
  deleting: boolean
  onClose: () => void
}) {
  const [deleteName, setDeleteName] = useState('')
  return (
    <ConfirmDialog
      key='account-delete'
      destructive
      open
      onOpenChange={(o) => {
        if (!o) onClose()
      }}
      handleConfirm={onRemove}
      isLoading={deleting}
      disabled={deleteName.trim() !== account.name}
      className='max-w-md'
      title={'删除账号 ' + account.name + '？'}
      desc={
        <>
          「{account.name}」（{account.platformCode}
          ）删除后其消息流断开；已有会话数据时后端会拒绝删除。该操作不可撤销。
          输入账号名称以确认：
        </>
      }
      confirmText='删除'
    >
      <Input
        value={deleteName}
        onChange={(e) => setDeleteName(e.target.value)}
        placeholder={account.name}
        autoComplete='off'
      />
    </ConfirmDialog>
  )
}
