import { useEffect, useState } from 'react'
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
  const [deleteName, setDeleteName] = useState('')
  const [deleting, setDeleting] = useState(false)

  const closeRowDialogs = () => {
    setOpen(null)
    setTimeout(() => setCurrentAccount(null), 500)
  }

  useEffect(() => {
    if (open === 'delete') setDeleteName('')
  }, [open])

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
        <ConfirmDialog
          key='account-delete'
          destructive
          open
          onOpenChange={(o) => {
            if (!o) closeRowDialogs()
          }}
          handleConfirm={remove}
          isLoading={deleting}
          disabled={deleteName.trim() !== currentAccount.name}
          className='max-w-md'
          title={'删除账号 ' + currentAccount.name + '？'}
          desc={
            <>
              「{currentAccount.name}」（{currentAccount.platformCode}
              ）删除后其消息流断开；已有会话数据时后端会拒绝删除。该操作不可撤销。
              输入账号名称以确认：
            </>
          }
          confirmText='删除'
        >
          <Input
            value={deleteName}
            onChange={(e) => setDeleteName(e.target.value)}
            placeholder={currentAccount.name}
            autoComplete='off'
          />
        </ConfirmDialog>
      ) : null}
    </>
  )
}
