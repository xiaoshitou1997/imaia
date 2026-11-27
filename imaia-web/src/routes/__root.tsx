import { useEffect } from 'react'
import { type QueryClient } from '@tanstack/react-query'
import {
  createRootRouteWithContext,
  Outlet,
  useMatches,
} from '@tanstack/react-router'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { TanStackRouterDevtools } from '@tanstack/react-router-devtools'
import { Toaster } from '@/components/ui/sonner'
import { NavigationProgress } from '@/components/navigation-progress'
import { GeneralError } from '@/features/errors/general-error'
import { NotFoundError } from '@/features/errors/not-found-error'

const PRODUCT_NAME = 'imaia 中枢'

/** 路由 id → 页面名；标题在根路由统一拼装，不在各页面零散赋值。 */
const PAGE_TITLES: Record<string, string> = {
  '/_authenticated': '控制台',
  '/_authenticated/': '概览',
  '/_authenticated/accounts': '账号',
  '/_authenticated/conversations': '会话',
  '/_authenticated/tasks': '任务',
  '/_authenticated/providers': '供应商',
  '/_authenticated/settings': '设置',
  '/sign-in': '登录',
}

function titleForRouteId(routeId: string): string | null {
  const normalized = routeId.replace('/_authenticated/errors/$error', '')
  return PAGE_TITLES[normalized] ?? PAGE_TITLES[routeId] ?? null
}

/** 页面标题：`页面名 · imaia 中枢`，与入口 HTML 的默认标题同一模板。 */
function RouteTitle() {
  const matches = useMatches()
  useEffect(() => {
    for (let i = matches.length - 1; i >= 0; i--) {
      const page = titleForRouteId(matches[i].routeId)
      if (page) {
        document.title = `${page} · ${PRODUCT_NAME}`
        return
      }
    }
    document.title = PRODUCT_NAME
  }, [matches])
  return null
}

export const Route = createRootRouteWithContext<{
  queryClient: QueryClient
}>()({
  component: () => {
    return (
      <>
        <RouteTitle />
        <NavigationProgress />
        <Outlet />
        <Toaster duration={5000} />
        {import.meta.env.MODE === 'development' && (
          <>
            <ReactQueryDevtools buttonPosition='bottom-left' />
            <TanStackRouterDevtools position='bottom-right' />
          </>
        )}
      </>
    )
  },
  notFoundComponent: NotFoundError,
  errorComponent: GeneralError,
})
