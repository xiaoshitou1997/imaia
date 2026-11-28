import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { ProfileDropdown } from '@/components/profile-dropdown'
import { ThemeSwitch } from '@/components/theme-switch'
import { api } from '@/lib/api'
import { Route } from '@/routes/_authenticated/conversations/'
import { ConversationList } from './components/conversation-list'
import { ConversationMessages } from './components/conversation-messages'
import { EditConversationDialog } from './components/edit-conversation-dialog'

export function Conversations() {
  // 搜索词与页码来自路由 search：URL 是唯一事实源，刷新与分享不丢。
  const routeSearch = Route.useSearch()
  const navigate = Route.useNavigate()
  const search = routeSearch.q ?? ''
  const page = routeSearch.page ?? 1
  const updateSearch = (patch: { q?: string; page?: number }) =>
    navigate({ search: (prev) => ({ ...prev, ...patch }) })
  const [explicitId, setSelectedId] = useState<number | null>(null)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [editing, setEditing] = useState(false)

  const listQuery = useQuery({
    queryKey: ['conversations', search, page],
    queryFn: () => api.conversations(search.trim() || undefined, page, 20),
  })
  const items = listQuery.data?.items ?? []

  // 首屏/翻页后落到第一个会话；用派生值而不是 effect+setState，
  // 避免「effect 里同步 setState 触发级联渲染」。
  const firstId = items.length ? items[0].id : null
  const selectedId = explicitId ?? firstId
  const selected = items.find((c) => c.id === selectedId) ?? null

  return (
    <>
      <Header fixed>
        <div className='ms-auto flex items-center gap-2'>
          <ThemeSwitch />
          <ProfileDropdown />
        </div>
      </Header>

      <Main fixed>
        <section className='flex h-full gap-6'>
          <ConversationList
            search={search}
            onSearchChange={(v) => {
              updateSearch({ q: v || undefined, page: undefined })
              setSelectedId(null)
            }}
            page={page}
            onPageChange={(p) => {
              updateSearch({ page: p > 1 ? p : undefined })
              setSelectedId(null)
            }}
            items={items}
            total={listQuery.data?.total ?? 0}
            totalPages={listQuery.data?.totalPages ?? 1}
            loading={listQuery.isLoading}
            isError={listQuery.isError}
            onRetry={() => void listQuery.refetch()}
            selectedId={selectedId}
            onSelect={(id) => {
              setSelectedId(id)
              setMobileOpen(true)
            }}
          />

          {selected ? (
            <ConversationMessages
              convo={selected}
              mobileOpen={mobileOpen}
              onBack={() => setMobileOpen(false)}
              onEdit={() => setEditing(true)}
            />
          ) : (
            <div
              className={
                'absolute inset-0 start-full z-50 hidden w-full flex-1 flex-col justify-center rounded-md border bg-card shadow-xs sm:static sm:z-auto sm:flex'
              }
            >
              <div className='flex flex-col items-center space-y-6'>
                <p className='text-sm text-muted-foreground'>
                  选择一个会话查看归档消息。
                </p>
              </div>
            </div>
          )}
        </section>
      </Main>

      <EditConversationDialog
        convo={selected}
        open={editing}
        onOpenChange={setEditing}
      />
    </>
  )
}
