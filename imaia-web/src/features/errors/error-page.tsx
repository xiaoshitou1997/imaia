import { useNavigate, useRouter } from '@tanstack/react-router'
import { Logo } from '@/assets/logo'
import { Button } from '@/components/ui/button'
import { t } from '@/lib/copy'

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
 * 品牌标识 + 产品名 + 可操作入口。文案走资源层，不在此硬编码。
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
            {t('product.name')}
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
              {t('action.back')}
            </Button>
            <Button onClick={() => navigate({ to: '/' })}>
              {t('action.home')}
            </Button>
          </div>
        )}
      </div>
    </div>
  )
}
