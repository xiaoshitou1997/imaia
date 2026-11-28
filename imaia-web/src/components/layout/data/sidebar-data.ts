import type { SidebarData } from '../types'
import { Link2, MessageSquare, Plug, Bot, LayoutDashboard, Clock } from 'lucide-react'

/**
 * 侧边栏与命令面板共用的导航数据。
 * 路由以 routes/_authenticated 下的实际页面为准，不保留脚手架演示项。
 */
export const sidebarData: SidebarData = {
  user: {
    name: 'hub 主人',
    email: '',
    avatar: '',
  },
  navGroups: [
    {
      title: '平台',
      items: [
        { title: '概览', url: '/', icon: LayoutDashboard },
        { title: '会话', url: '/conversations', icon: MessageSquare },
      ],
    },
    {
      title: '接入',
      items: [
        { title: '接入连接', url: '/accounts', icon: Link2 },
        { title: '模型供应商', url: '/providers', icon: Plug },
        { title: '定时任务', url: '/tasks', icon: Clock },
        {
          title: '设置',
          items: [
            { title: '设置', url: '/settings' },
            { title: '图像', url: '/settings/image' },
            { title: '网页搜索', url: '/settings/search' },
            { title: '视频', url: '/settings/video' },
          ],
          icon: Bot,
        },
      ],
    },
  ],
}
