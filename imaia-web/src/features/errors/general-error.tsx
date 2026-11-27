import { ErrorPage } from './error-page'

type GeneralErrorProps = React.HTMLAttributes<HTMLDivElement> & {
  minimal?: boolean
}

/** 渲染异常兜底：不白屏、不吞错，给出可操作入口。 */
export function GeneralError({ minimal = false }: GeneralErrorProps) {
  return (
    <ErrorPage
      code='500'
      title='出了点问题'
      description='服务暂时异常，请稍后重试；若持续失败请查看服务日志。'
      minimal={minimal}
    />
  )
}
