import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { formatDistanceToNow } from 'date-fns'
import { zhCN } from 'date-fns/locale'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { api, ApiError } from '@/lib/api'
import { t } from '@/lib/copy'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'

export function ConversationMembers({ convoId }: { convoId: number }) {
  const contacts = useQuery({
    queryKey: ['conversation-contacts', convoId],
    queryFn: () => api.conversationContacts(convoId),
  })
  const analysis = useQuery({
    queryKey: ['conversation-analysis', convoId],
    queryFn: () => api.conversationAnalysis(convoId),
  })
  const queryClient = useQueryClient()
  const [busyContact, setBusyContact] = useState<number | null>(null)
  const [expandedContact, setExpandedContact] = useState<number | null>(null)

  const profileByContact = new Map(
    (analysis.data ?? []).map((a) => [a.contactId, a])
  )

  const runAnalysis = (contactId: number) => {
    setBusyContact(contactId)
    api
      .runAnalysis(convoId, contactId)
      .then(() => {
        toast.success('分析完成')
        queryClient.invalidateQueries({
          queryKey: ['conversation-analysis', convoId],
        })
        queryClient.invalidateQueries({
          queryKey: ['conversation-contacts', convoId],
        })
      })
      .catch((e: unknown) =>
        toast.error(e instanceof ApiError ? e.message : '分析失败')
      )
      .finally(() => setBusyContact(null))
  }

  const rows = contacts.data ?? []
  // 加载失败与「成员快照未建立」是两回事，不能共用同一个空态文案。
  if (contacts.isError) {
    return (
      <div className='flex flex-col items-start gap-2 py-6'>
        <p className='text-sm text-muted-foreground'>{t('state.loadFailed')}</p>
        <Button variant='outline' size='sm' onClick={() => void contacts.refetch()}>
          {t('action.retry')}
        </Button>
      </div>
    )
  }
  if (contacts.isLoading) {
    return <p className='py-6 text-sm text-muted-foreground'>加载中…</p>
  }
  if (!rows.length) {
    return (
      <p className='py-6 text-sm text-muted-foreground'>
        成员快照还没有建立，等消息归档后自动生成。
      </p>
    )
  }

  return (
    <div>
      {rows.map((c) => {
        const profile = profileByContact.get(c.contactId)
        const expanded = expandedContact === c.id
        return (
          <div key={c.id}>
            <div className='flex items-center gap-3 border-b py-2 last:border-b-0'>
              <Avatar className='size-8'>
                <AvatarFallback>
                  {(c.displayName || '?').slice(0, 1)}
                </AvatarFallback>
              </Avatar>
              <div className='min-w-0 flex-1'>
                <div className='flex items-center gap-2'>
                  <span className='truncate text-sm font-medium'>
                    {c.displayName || '未命名成员'}
                  </span>
                  <Badge variant='outline'>{c.role}</Badge>
                  {c.analyzed ? <Badge>已画像</Badge> : null}
                </div>
                {c.lastSeenAt ? (
                  <p className='text-xs text-muted-foreground'>
                    最后活跃{' '}
                    {formatDistanceToNow(new Date(c.lastSeenAt), {
                      addSuffix: true,
                      locale: zhCN,
                    })}
                  </p>
                ) : null}
              </div>
              {profile ? (
                <Button
                  variant='ghost'
                  size='sm'
                  onClick={() => setExpandedContact(expanded ? null : c.id)}
                >
                  {expanded ? '收起画像' : '画像'}
                </Button>
              ) : null}
              <Button
                variant='outline'
                size='sm'
                disabled={busyContact !== null}
                onClick={() => runAnalysis(c.contactId)}
              >
                {busyContact === c.contactId ? (
                  <Loader2 className='size-4 animate-spin' />
                ) : null}
                {c.analyzed ? '重新分析' : '分析'}
              </Button>
            </div>
            {expanded && profile ? (
              <div className='mb-2 rounded-md bg-muted/50 p-3 text-sm whitespace-pre-wrap'>
                {profile.profile}
                <p className='mt-2 text-xs text-muted-foreground'>
                  基于 {profile.messageCount ?? '—'} 条消息 ·{' '}
                  {formatDistanceToNow(new Date(profile.generatedAt), {
                    addSuffix: true,
                    locale: zhCN,
                  })}
                  生成
                </p>
              </div>
            ) : null}
          </div>
        )
      })}
    </div>
  )
}
