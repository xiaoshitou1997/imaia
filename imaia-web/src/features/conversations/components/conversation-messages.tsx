import { Fragment } from 'react/jsx-runtime'
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { format } from 'date-fns'
import {
  ArrowLeft,
  Bot,
  History,
  Loader2,
  Paperclip,
  Pencil,
  Search,
} from 'lucide-react'
import { toast } from 'sonner'
import { cn } from '@/lib/utils'
import {
  api,
  attachmentContentUrl,
  ApiError,
  type Conversation,
  type MessageRow,
} from '@/lib/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ConversationMembers } from './conversation-members'

interface ConversationMessagesProps {
  convo: Conversation
  mobileOpen: boolean
  onBack: () => void
  onEdit: () => void
}

export function ConversationMessages({
  convo,
  mobileOpen,
  onBack,
  onEdit,
}: ConversationMessagesProps) {
  const [keywordDraft, setKeywordDraft] = useState('')
  const [keyword, setKeyword] = useState('')
  const [syncing, setSyncing] = useState(false)
  const queryClient = useQueryClient()

  const messagesQuery = useQuery({
    queryKey: ['conversation-messages', convo.id, keyword],
    queryFn: () => api.conversationMessages(convo.id, keyword || undefined),
  })
  const data = messagesQuery.data

  // 后端按新→旧返回；模板气泡流是 flex-col-reverse，直接按序渲染即 newest 落底
  const groups = (data ?? []).reduce<Record<string, MessageRow[]>>((acc, m) => {
    const key = format(new Date(m.occurredAt), 'yyyy年M月d日')
    if (!acc[key]) acc[key] = []
    acc[key].push(m)
    return acc
  }, {})

  const syncHistory = () => {
    setSyncing(true)
    api
      .syncHistory(convo.id)
      .then((r) => {
        toast.success(
          '扫描 ' + r.scanned + ' 条，新增 ' + r.added + ' 条（' + r.pages + ' 页）'
        )
        queryClient.invalidateQueries({
          queryKey: ['conversation-messages', convo.id],
        })
      })
      .catch((e: unknown) =>
        toast.error(e instanceof ApiError ? e.message : '同步失败')
      )
      .finally(() => setSyncing(false))
  }

  return (
    <div
      className={cn(
        'absolute inset-0 start-full z-50 hidden w-full flex-1 flex-col border bg-background shadow-xs sm:static sm:z-auto sm:flex sm:rounded-md',
        mobileOpen && 'inset-s-0 flex'
      )}
    >
      {/* 会话头 */}
      <div className='mb-1 flex flex-none justify-between gap-2 bg-card p-4 shadow-lg sm:rounded-t-md'>
        <div className='flex min-w-0 gap-3'>
          <Button
            size='icon'
            variant='ghost'
            className='-ms-2 h-full sm:hidden'
            onClick={onBack}
          >
            <ArrowLeft className='rtl:rotate-180' />
          </Button>
          <div className='min-w-0'>
            <div className='flex items-center gap-2'>
              <span className='truncate text-sm font-medium lg:text-base'>
                {convo.displayName}
              </span>
              {convo.kind === 'group' ? (
                <Badge variant='outline'>群聊</Badge>
              ) : (
                <Badge variant='outline'>单聊</Badge>
              )}
              <Badge variant={convo.autoreply ? 'default' : 'outline'}>
                {convo.autoreply ? '自动回复开' : '自动回复关'}
              </Badge>
            </div>
            <p className='truncate text-xs text-muted-foreground lg:text-sm'>
              {convo.platformCode ?? '—'}
              {convo.connectionName ? ' · ' + convo.connectionName : ''} ·{' '}
              {convo.messageCount} 条消息
            </p>
          </div>
        </div>

        <div className='flex flex-none items-center gap-1 lg:gap-2'>
          <Button
            variant='outline'
            size='sm'
            onClick={syncHistory}
            disabled={syncing}
          >
            {syncing ? (
              <Loader2 className='animate-spin' />
            ) : (
              <History className='size-4' />
            )}
            同步历史
          </Button>
          <Button variant='outline' size='sm' onClick={onEdit}>
            <Pencil className='size-4' /> 编辑
          </Button>
        </div>
      </div>

      <Tabs
        defaultValue='messages'
        className='flex min-h-0 flex-1 flex-col gap-2 px-4 pb-4'
      >
        <TabsList className='w-fit'>
          <TabsTrigger value='messages'>消息</TabsTrigger>
          <TabsTrigger value='members'>成员</TabsTrigger>
        </TabsList>

        <TabsContent value='messages' className='flex min-h-0 flex-1 flex-col'>
          <div className='mb-2 flex flex-none items-center gap-2'>
            <div className='relative max-w-xs flex-1'>
              <Search
                size={14}
                className='absolute start-2.5 top-1/2 -translate-y-1/2 text-muted-foreground'
              />
              <Input
                className='ps-8'
                placeholder='会话内搜索正文，回车确认'
                value={keywordDraft}
                onChange={(e) => setKeywordDraft(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') setKeyword(keywordDraft.trim())
                }}
              />
            </div>
            {keyword ? (
              <Button
                variant='ghost'
                size='sm'
                onClick={() => {
                  setKeywordDraft('')
                  setKeyword('')
                }}
              >
                清除
              </Button>
            ) : null}
          </div>

          <div className='chat-text-container relative -me-4 flex min-h-0 flex-1 flex-col overflow-y-hidden'>
            {messagesQuery.isLoading ? (
              <div className='flex flex-1 items-center justify-center text-sm text-muted-foreground'>
                加载中…
              </div>
            ) : (
              <div className='chat-flex flex h-full w-full grow flex-col-reverse justify-start gap-4 overflow-y-auto py-2 pe-4 pb-4'>
                {data && !data.length ? (
                  <p className='m-auto text-sm text-muted-foreground'>
                    这个会话还没有归档消息，试试右上角「同步历史」。
                  </p>
                ) : null}
                {groups &&
                  Object.keys(groups).map((key) => (
                    <Fragment key={key}>
                      {groups[key].map((m) => (
                        <MessageBubble key={m.id} m={m} convoId={convo.id} />
                      ))}
                      <div className='text-center text-xs text-muted-foreground'>
                        {key}
                      </div>
                    </Fragment>
                  ))}
              </div>
            )}
          </div>
        </TabsContent>

        <TabsContent
          value='members'
          className='min-h-0 flex-1 overflow-y-auto pe-1'
        >
          <ConversationMembers convoId={convo.id} />
        </TabsContent>
      </Tabs>
    </div>
  )
}

function MessageBubble({ m, convoId }: { m: MessageRow; convoId: number }) {
  const outgoing = m.direction === 'outgoing'
  return (
    <div
      className={cn(
        'chat-box max-w-72 px-3 py-2 wrap-break-word shadow-lg',
        outgoing
          ? 'self-end rounded-[16px_16px_0_16px] bg-primary/90 text-primary-foreground'
          : 'self-start rounded-[16px_16px_16px_0] bg-muted'
      )}
    >
      {!outgoing && m.senderName ? (
        <span className='mb-1 block text-xs font-medium opacity-80'>
          {m.senderName}
        </span>
      ) : null}
      {m.origin === 'ai' ? (
        <span className='mb-1 flex items-center gap-1 text-xs italic opacity-80'>
          <Bot className='size-3' /> AI 回复
        </span>
      ) : null}
      {m.body ? <p className='whitespace-pre-wrap'>{m.body}</p> : null}
      {m.attachments.length ? (
        <div className='mt-1 space-y-1'>
          {m.attachments.map((a) => (
            <a
              key={a.id}
              href={attachmentContentUrl(convoId, a.id)}
              target='_blank'
              rel='noreferrer'
              className='flex items-center gap-1 text-xs underline underline-offset-2'
            >
              <Paperclip className='size-3' />
              {a.filename || a.kind}
              {a.sizeBytes
                ? '（' + Math.max(1, Math.round(a.sizeBytes / 1024)) + ' KB）'
                : ''}
            </a>
          ))}
        </div>
      ) : null}
      {!m.body && !m.attachments.length ? (
        <p className='text-xs italic opacity-70'>[{m.kind}]</p>
      ) : null}
      <span
        className={cn(
          'mt-1 block text-xs font-light italic',
          outgoing ? 'text-end opacity-80' : 'opacity-70'
        )}
      >
        {format(new Date(m.occurredAt), 'HH:mm')}
      </span>
    </div>
  )
}
