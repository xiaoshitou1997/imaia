import { ToolSettings } from '../tools/tool-settings'

export function ImageSettings() {
  return (
    <ToolSettings
      title="图像工具"
      description="独立的图像生成端点（tool_call），不依赖聊天供应商。"
      capability="images"
      fields={[
    {
      "key": "imageBaseUrl",
      "label": "Base URL",
      "placeholder": "https://api.example.com/v1"
    },
    {
      "key": "imageApiKey",
      "label": "API Key",
      "password": true
    },
    {
      "key": "imageModel",
      "label": "模型",
      "placeholder": "例如 flux.1-schnell"
    }
  ]}
    />
  )
}