import { useNavigate, useRouter } from '@tanstack/react-router'
import { Logo } from '@/assets/logo'
import { Button } from '@/components/ui/button'

interface ErrorPageProps {
  /** 错误语义码，仅作视觉标识。 */
  code: string
  title: string
  description: string
  /** shadcn 风格的视觉密度；minimal 用于嵌在页面内的错误边界兜底。 */
  minimal?: boolean
}

/**
 * 系统页（404/403/500/维护）与错误边界兜底的统一外壳：
 * 品牌标识 + 产品名 + 中文可操作入口。
 *
 * 脚手架自带的是英文示例文案（"Oops! Page Not Found!"），交付前必须替换——
 * 这是用户看到的第一屏。
 */
export function ErrorPage({
  code,
  title,
  description,
  minimal = false,
}: ErrorPageProps) {
  const navigate = useNavigate()
  const { history } = useRouter()

  return (
    <div className='h-svh w-full'>
      <div className='m-auto flex h-full w-full flex-col items-center justify-center gap-3 px-6 text-center'>
        {!minimal && <Logo className='size-10 text-primary' />}
        {!minimal && (
          <span className='text-sm font-medium text-muted-foreground'>
            imaia 中枢
          </span>
        )}
        {!minimal && (
          <h1 className='text-6xl font-bold leading-tight tracking-tight'>
            {code}
          </h1>
        )}
        <span className='text-lg font-medium'>{title}</span>
        <p className='max-w-md text-sm text-muted-foreground'>{description}</p>
        {!minimal && (
          <div className='mt-4 flex gap-3'>
            <Button variant='outline' onClick={() => history.go(-1)}>
              返回上一页
            </Button>
            <Button onClick={() => navigate({ to: '/' })}>回到首页</Button>
          </div>
        )}
      </div>
    </div>
  )
}
