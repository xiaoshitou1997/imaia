import { ToolSettings } from '../tools/tool-settings'

export function SearchSettings() {
  return (
    <ToolSettings
      title="网络搜索"
      description="独立的网页搜索端点（tool_call），AI 需要时自动调用。"
      capability="search"
      fields={[
    {
      "key": "webSearchBaseUrl",
      "label": "Base URL",
      "placeholder": "https://api.example.com"
    },
    {
      "key": "webSearchApiKey",
      "label": "API Key",
      "password": true
    }
  ]}
    />
  )
}