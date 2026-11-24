import { ToolSettings } from '../tools/tool-settings'

export function VideoSettings() {
  return (
    <ToolSettings
      title="视频工具"
      description="独立的视频生成端点（tool_call），不依赖聊天供应商。"
      capability="video"
      fields={[
    {
      "key": "videoBaseUrl",
      "label": "Base URL",
      "placeholder": "https://api.example.com/v1"
    },
    {
      "key": "videoApiKey",
      "label": "API Key",
      "password": true
    },
    {
      "key": "videoModel",
      "label": "模型",
      "placeholder": "例如 cogvideox"
    }
  ]}
    />
  )
}