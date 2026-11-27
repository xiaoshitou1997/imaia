import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Loader2, RefreshCw, Search } from 'lucide-react'
import { toast } from 'sonner'
import { api, ApiError, type ModelRow } from '@/lib/api'
import { t } from '@/lib/copy'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { useProviders } from './providers-provider'

export function ProviderModelsDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { currentProvider } = useProviders()
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const [syncing, setSyncing] = useState(false)
  const [toggling, setToggling] = useState<number | null>(null)

  const providers = useQuery({
    queryKey: ['providers'],
    queryFn: api.providers,
    enabled: open,
  })
  const provider = (providers.data ?? []).find(
    (p) => p.id === currentProvider?.id
  )

  const models = useMemo(() => {
    const list = provider?.models ?? []
    const needle = keyword.trim().toLowerCase()
    if (!needle) return list
    return list.filter(
      (m) =>
        m.modelKey.toLowerCase().includes(needle) ||
        (m.name ?? '').toLowerCase().includes(needle)
    )
  }, [provider, keyword])

  const sync = () => {
    if (!provider) return
    setSyncing(true)
    api
      .syncModels(provider.id)
      .then((r) =>
        toast.success(
          '已同步 ' + r.listed + ' 个：新增 ' + r.added + '，更新 ' + r.updated +
            '，下线 ' + r.retired
        )
      )
      .catch((e: unknown) =>
        toast.error(e instanceof ApiError ? e.message : '同步失败')
      )
      .finally(() => {
        setSyncing(false)
        queryClient.invalidateQueries({ queryKey: ['providers'] })
        queryClient.invalidateQueries({ queryKey: ['runtime'] })
      })
  }

  const toggleModel = (model: ModelRow, enabled: boolean) => {
    setToggling(model.id)
    api
      .updateModel(model.id, { enabled })
      .then(() => {
        queryClient.invalidateQueries({ queryKey: ['providers'] })
        queryClient.invalidateQueries({ queryKey: ['runtime'] })
        return null
      })
      .catch((e: unknown) =>
        toast.error(e instanceof ApiError ? e.message : '操作失败')
      )
      .finally(() => setToggling(null))
  }

  if (!currentProvider) return null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className='flex max-h-svh flex-col sm:max-w-3xl'>
        <DialogHeader>
          <DialogTitle>模型目录 · {currentProvider.name}</DialogTitle>
          <DialogDescription>
            启用的模型才参与聊天槽位推导；目录来自 /v1/models 同步。
          </DialogDescription>
        </DialogHeader>

        <div className='flex flex-none items-center gap-2'>
          <div className='relative flex-1'>
            <Search
              size={14}
              className='absolute start-2.5 top-1/2 -translate-y-1/2 text-muted-foreground'
            />
            <Input
              className='ps-8'
              placeholder='按 modelKey 或名称过滤'
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
          </div>
          <Button variant='outline' onClick={sync} disabled={syncing}>
            {syncing ? (
              <Loader2 className='animate-spin' />
            ) : (
              <RefreshCw className='size-4' />
            )}
            同步模型
          </Button>
        </div>

        <div className='min-h-0 flex-1 overflow-y-auto rounded-md border'>
          {providers.isError ? (
            <div className='flex flex-col items-center gap-2 py-8'>
              <p className='text-sm text-muted-foreground'>
                {t('state.loadFailed')}
              </p>
              <Button
                variant='outline'
                size='sm'
                onClick={() => void providers.refetch()}
              >
                {t('action.retry')}
              </Button>
            </div>
          ) : providers.isLoading ? (
            <p className='py-8 text-center text-sm text-muted-foreground'>
              加载中…
            </p>
          ) : models.length ? (
            models.map((m) => (
              <div
                key={m.id}
                className='flex items-center gap-3 border-b px-3 py-2 last:border-b-0'
              >
                <div className='min-w-0 flex-1'>
                  <div className='flex items-center gap-2'>
                    <code className='truncate text-xs font-medium'>
                      {m.modelKey}
                    </code>
                    {!m.available ? (
                      <Badge variant='destructive'>不可用</Badge>
                    ) : null}
                  </div>
                  <div className='flex items-center gap-1.5 pt-0.5'>
                    <span className='truncate text-xs text-muted-foreground'>
                      {m.name ?? m.description ?? '—'}
                    </span>
                    {m.supportsVision ? (
                      <Badge variant='outline' className='text-xs'>视觉</Badge>
                    ) : null}
                    {m.supportsReasoning ? (
                      <Badge variant='outline' className='text-xs'>推理</Badge>
                    ) : null}
                    {m.supportsToolCalls ? (
                      <Badge variant='outline' className='text-xs'>工具</Badge>
                    ) : null}
                  </div>
                </div>
                <Switch
                  checked={m.enabled}
                  disabled={toggling === m.id}
                  onCheckedChange={(v) => toggleModel(m, v)}
                  aria-label='启用模型'
                />
              </div>
            ))
          ) : (
            <p className='py-8 text-center text-sm text-muted-foreground'>
              {keyword ? '没有匹配的模型。' : '还没有模型，点「同步模型」拉取。'}
            </p>
          )}
        </div>

        <DialogFooter className='text-xs text-muted-foreground'>
          {provider?.sync ?? ''}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
