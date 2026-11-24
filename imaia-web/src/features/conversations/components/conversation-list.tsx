import { Fragment } from 'react/jsx-runtime'
import { formatDistanceToNow } from 'date-fns'
import { zhCN } from 'date-fns/locale'
import { ChevronLeft, ChevronRight, MessagesSquare, Search } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Separator } from '@/components/ui/separator'
import { type Conversation } from '@/lib/api'

interface ConversationListProps {
  search: string
  onSearchChange: (v: string) => void
  page: number
  onPageChange: (p: number) => void
  items: Conversation[]
  total: number
  totalPages: number
  loading: boolean
  selectedId: number | null
  onSelect: (id: number) => void
}

export function ConversationList({
  search,
  onSearchChange,
  page,
  onPageChange,
  items,
  total,
  totalPages,
  loading,
  selectedId,
  onSelect,
}: ConversationListProps) {
  return (
    <div className='flex w-full flex-col gap-2 sm:w-56 lg:w-72 2xl:w-80'>
      <div className='sticky top-0 z-10 -mx-4 bg-background px-4 pb-3 shadow-md sm:static sm:z-auto sm:mx-0 sm:p-0 sm:shadow-none'>
        <div className='flex items-center justify-between py-2'>
          <div className='flex gap-2'>
            <h1 className='text-2xl font-bold'>会话</h1>
            <MessagesSquare size={20} className='self-center text-muted-foreground' />
          </div>
        </div>

        <label
          className={cn(
            'focus-within:ring-1 focus-within:ring-ring focus-within:outline-hidden',
            'flex h-10 w-full items-center space-x-0 rounded-md border border-border ps-2'
          )}
        >
          <Search size={15} className='me-2 stroke-slate-500' />
          <span className='sr-only'>搜索会话</span>
          <input
            type='text'
            className='w-full flex-1 bg-inherit text-sm focus-visible:outline-hidden'
            placeholder='搜索会话…'
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
          />
        </label>
      </div>

      <ScrollArea className='-mx-3 h-full overflow-scroll p-3'>
        {items.map((c) => (
          <Fragment key={c.id}>
            <button
              type='button'
              className={cn(
                'group hover:bg-accent hover:text-accent-foreground',
                'flex w-full rounded-md px-2 py-2 text-start text-sm',
                selectedId === c.id && 'sm:bg-muted'
              )}
              onClick={() => onSelect(c.id)}
            >
              <div className='flex gap-2'>
                <Avatar>
                  <AvatarFallback>
                    {(c.title || c.platformId || '?').slice(0, 1)}
                  </AvatarFallback>
                </Avatar>
                <div className='min-w-0'>
                  <span className='block truncate font-medium'>
                    {c.title || c.platformId}
                  </span>
                  <span className='block truncate text-xs text-muted-foreground group-hover:text-accent-foreground/90'>
                    {c.platformCode ?? '—'} · {c.messageCount} 条
                    {c.lastMessageAt
                      ? ' · ' +
                        formatDistanceToNow(new Date(c.lastMessageAt), {
                          addSuffix: true,
                          locale: zhCN,
                        })
                      : ''}
                  </span>
                </div>
              </div>
            </button>
            <Separator className='my-1' />
          </Fragment>
        ))}
        {!items.length && !loading ? (
          <p className='px-2 py-6 text-sm text-muted-foreground'>
            没有匹配的会话。
          </p>
        ) : null}
      </ScrollArea>

      <div className='flex items-center justify-between px-1 pb-1 text-xs text-muted-foreground'>
        <span>共 {total} 个</span>
        <div className='flex items-center gap-1'>
          <Button
            variant='outline'
            size='sm'
            className='h-7 px-2'
            disabled={page <= 1}
            onClick={() => onPageChange(page - 1)}
          >
            <ChevronLeft /> 上一页
          </Button>
          <span className='tabular-nums'>
            {page}/{totalPages}
          </span>
          <Button
            variant='outline'
            size='sm'
            className='h-7 px-2'
            disabled={page >= totalPages}
            onClick={() => onPageChange(page + 1)}
          >
            下一页 <ChevronRight />
          </Button>
        </div>
      </div>
    </div>
  )
}
