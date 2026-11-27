import { ErrorPage } from './error-page'

export function ForbiddenError() {
  return (
    <ErrorPage
      code='403'
      title='没有访问权限'
      description='当前会话无权查看该页面。'
    />
  )
}
