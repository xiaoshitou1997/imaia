import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api, ApiError } from '@/lib/api'
import { ConfirmDialog } from '@/components/confirm-dialog'
import { Input } from '@/components/ui/input'
import { ProviderDialog } from './provider-dialog'
import { ProviderModelsDialog } from './provider-models-dialog'
import { useProviders } from './providers-provider'

export function ProvidersDialogs() {
  const { open, setOpen, currentProvider, setCurrentProvider } = useProviders()
  const queryClient = useQueryClient()
  const [deleting, setDeleting] = useState(false)

  const closeRowDialogs = () => {
    setOpen(null)
    setTimeout(() => setCurrentProvider(null), 500)
  }

  const remove = () => {
    if (!currentProvider) return
    setDeleting(true)
    api
      .deleteProvider(currentProvider.id)
      .then(() => {
        toast.success('供应商已删除')
        queryClient.invalidateQueries({ queryKey: ['providers'] })
        queryClient.invalidateQueries({ queryKey: ['runtime'] })
        closeRowDialogs()
      })
      .catch((e: unknown) =>
        toast.error(e instanceof ApiError ? e.message : '删除失败')
      )
      .finally(() => setDeleting(false))
  }

  const editing =
    (open === 'edit' || open === 'create') && currentProvider
      ? {
          id: currentProvider.id,
          name: currentProvider.name,
          baseUrl: currentProvider.baseUrl,
          apiKey: currentProvider.apiKey,
          modelsDevId: currentProvider.modelsDevId,
        }
      : null

  return (
    <>
      <ProviderDialog
        key={'provider-dialog-' + (editing?.id ?? 'create')}
        provider={editing}
        open={open === 'create' || open === 'edit'}
        onOpenChange={(o) => {
          if (!o) closeRowDialogs()
        }}
      />

      <ProviderModelsDialog
        open={open === 'models'}
        onOpenChange={(o) => {
          if (!o) closeRowDialogs()
        }}
      />

      {currentProvider && open === 'delete' ? (
        <DeleteProviderConfirm provider={currentProvider} onRemove={remove} deleting={deleting}
          onClose={closeRowDialogs} />
      ) : null}
    </>
  )
}

/**
 * 删除确认输入独立成组件：仅在删除对话框打开时挂载，输入值随重挂自然清空，
 * 不需要在 effect 里同步 setState（级联渲染）。
 */
function DeleteProviderConfirm({ provider, onRemove, deleting, onClose }: {
  provider: NonNullable<ReturnType<typeof useProviders>['currentProvider']>
  onRemove: () => void
  deleting: boolean
  onClose: () => void
}) {
  const [deleteName, setDeleteName] = useState('')
  return (
    <ConfirmDialog
      key='provider-delete'
      destructive
      open
      onOpenChange={(o) => {
        if (!o) onClose()
      }}
      handleConfirm={onRemove}
      isLoading={deleting}
      disabled={deleteName.trim() !== provider.name}
      className='max-w-md'
      title={'删除供应商 ' + provider.name + '？'}
      desc={
        <>
          「{provider.name}」删除后其模型目录一并移除，引用它的槽位会推导失败。该操作不可撤销。
          输入供应商名称以确认：
        </>
      }
      confirmText='删除'
    >
      <Input
        value={deleteName}
        onChange={(e) => setDeleteName(e.target.value)}
        placeholder={provider.name}
        autoComplete='off'
      />
    </ConfirmDialog>
  )
}
