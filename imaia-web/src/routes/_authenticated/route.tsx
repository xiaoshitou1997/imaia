import { createFileRoute, redirect } from '@tanstack/react-router'
import { getCookie } from '@/lib/cookies'
import { AuthenticatedLayout } from '@/components/layout/authenticated-layout'

const ACCESS_TOKEN = 'thisisjustarandomstring'

export const Route = createFileRoute('/_authenticated')({
  beforeLoad: ({ location }) => {
    // 控制台用本地 cookie 会话代替 Clerk；无 token 一律送回登录页
    if (!getCookie(ACCESS_TOKEN)) {
      throw redirect({
        to: '/sign-in',
        search: { redirect: location.pathname },
      })
    }
  },
  component: AuthenticatedLayout,
})
