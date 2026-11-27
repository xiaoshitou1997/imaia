import { AlertCircle, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { t } from '@/lib/copy'

interface QueryStateProps {
  /** First load in flight; renders the skeleton instead of stale content. */
  isLoading: boolean
  /** Request failed; takes precedence over empty so failures are not shown as "no data". */
  isError: boolean
  /** Empty result set from a successful request. */
  isEmpty?: boolean
  errorText?: string
  emptyText?: string
  onRetry?: () => void
  children: React.ReactNode
}

/**
 * The single place a list/form decides between loading, failed and empty.
 * Pages used to fold "request failed" into the empty state, so a network error
 * looked like "there is nothing here" and offered no way back.
 */
export function QueryState({
  isLoading,
  isError,
  isEmpty = false,
  errorText = t('state.loadFailed'),
  emptyText = t('state.empty'),
  onRetry,
  children,
}: QueryStateProps) {
  if (isError) {
    return (
      <div
        role='alert'
        className='flex flex-col items-center justify-center gap-3 rounded-md border border-destructive/40 bg-destructive/5 p-6 text-center'
      >
        <AlertCircle className='size-5 text-destructive' />
        <p className='text-sm text-muted-foreground'>{errorText}</p>
        {onRetry ? (
          <Button type='button' variant='outline' size='sm' onClick={onRetry}>
            {t('action.retry')}
          </Button>
        ) : null}
      </div>
    )
  }

  if (isLoading) {
    return (
      <div
        role='status'
        aria-live='polite'
        className='flex items-center justify-center gap-2 p-6 text-sm text-muted-foreground'
      >
        <Loader2 className='size-4 animate-spin' />
        {t('state.loading')}
      </div>
    )
  }

  if (isEmpty) {
    return (
      <p className='p-6 text-center text-sm text-muted-foreground'>{emptyText}</p>
    )
  }

  return <>{children}</>
}
