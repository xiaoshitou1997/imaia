import { ErrorPage } from './error-page'

export function NotFoundError() {
  return (
    <ErrorPage
      code='404'
      title='页面不存在'
      description='你访问的地址不存在，或该页面已被移除。'
    />
  )
}
