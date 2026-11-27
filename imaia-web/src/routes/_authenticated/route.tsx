import { createFileRoute, redirect } from '@tanstack/react-router'
import { getCookie } from '@/lib/cookies'
import { AuthenticatedLayout } from '@/components/layout/authenticated-layout'

const ACCESS_TOKEN = 'thisisjustarandomstring'

export const Route = createFileRoute('/_authenticated')({
  beforeLoad: ({ location }) => {
    // 控制台是单管理员本地工具端：cookie 会话只做前端门禁，真正的授权在服务端。
    // 无 token 一律送回登录页。
    if (!getCookie(ACCESS_TOKEN)) {
      throw redirect({
        to: '/sign-in',
        search: { redirect: location.pathname },
      })
    }
  },
  component: AuthenticatedLayout,
})
