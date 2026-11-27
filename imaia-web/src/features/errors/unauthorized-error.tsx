import { ErrorPage } from './error-page'

export function UnauthorisedError() {
  return (
    <ErrorPage
      code='401'
      title='需要登录'
      description='登录状态已失效，请重新登录。'
    />
  )
}
