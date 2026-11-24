import { useQuery } from '@tanstack/react-query'
import { formatDistanceToNow } from 'date-fns'
import { zhCN } from 'date-fns/locale'
import { api } from '@/lib/api'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'

export function RecentActivity() {
  const { data } = useQuery({
    queryKey: ['recent-messages'],
    queryFn: api.recentMessages,
  })
  const items = data ?? []

  if (!items.length) {
    return (
      <p className='text-sm text-muted-foreground'>
        还没有消息。到「平台账号」接入一个账号后，聚合消息会出现在这里。
      </p>
    )
  }

  return (
    <div className='space-y-5'>
      {items.map((m) => (
        <div key={m.id} className='flex items-start gap-3'>
          <Avatar className='size-8'>
            <AvatarFallback>{(m.sender || '?').slice(0, 1)}</AvatarFallback>
          </Avatar>
          <div className='min-w-0 flex-1 space-y-1'>
            <p className='truncate text-sm font-medium leading-none'>
              {m.conversationLabel}
              <span className='ms-1.5 font-normal text-muted-foreground'>
                {m.sender}
              </span>
            </p>
            <p className='truncate text-sm text-muted-foreground'>
              {m.body || '[' + m.kind + ']'}
            </p>
          </div>
          <time className='whitespace-nowrap pt-0.5 text-xs text-muted-foreground'>
            {formatDistanceToNow(new Date(m.occurredAt), {
              addSuffix: true,
              locale: zhCN,
            })}
          </time>
        </div>
      ))}
    </div>
  )
}
