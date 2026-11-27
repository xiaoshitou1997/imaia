import { ErrorPage } from './error-page'

export function MaintenanceError() {
  return (
    <ErrorPage
      code='503'
      title='服务维护中'
      description='服务正在维护，稍后再试。'
    />
  )
}
