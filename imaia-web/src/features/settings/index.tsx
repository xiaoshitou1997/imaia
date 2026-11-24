import { Outlet } from '@tanstack/react-router'
import { Bot, ImageIcon, Search, Clapperboard } from 'lucide-react'
import { Separator } from '@/components/ui/separator'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { ProfileDropdown } from '@/components/profile-dropdown'
import { ThemeSwitch } from '@/components/theme-switch'
import { SidebarNav } from './components/sidebar-nav'

const sidebarNavItems = [
  {
    title: '助手',
    href: '/settings',
    icon: <Bot size={18} />,
  },
  {
    title: '图像工具',
    href: '/settings/image',
    icon: <ImageIcon size={18} />,
  },
  {
    title: '视频工具',
    href: '/settings/video',
    icon: <Clapperboard size={18} />,
  },
  {
    title: '网络搜索',
    href: '/settings/search',
    icon: <Search size={18} />,
  },
]

export function Settings() {
  return (
    <>
      <Header fixed>
        <div className='ms-auto flex items-center gap-2'>
          <ThemeSwitch />
          <ProfileDropdown />
        </div>
      </Header>

      <Main fixed>
        <div className='space-y-0.5'>
          <h1 className='text-2xl font-bold tracking-tight md:text-3xl'>
            设置
          </h1>
          <p className='text-muted-foreground'>
            AI 助手护栏与工具端点配置。工具端点是独立的，不依赖供应商。
          </p>
        </div>
        <Separator className='my-4 lg:my-6' />
        <div className='flex flex-1 flex-col space-y-2 overflow-hidden md:space-y-2 lg:flex-row lg:space-y-0 lg:space-x-12'>
          <aside className='top-0 lg:sticky lg:w-1/5'>
            <SidebarNav items={sidebarNavItems} />
          </aside>
          <div className='flex w-full overflow-y-hidden p-1'>
            <Outlet />
          </div>
        </div>
      </Main>
    </>
  )
}
