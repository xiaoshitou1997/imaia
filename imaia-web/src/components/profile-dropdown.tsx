import { Link } from '@tanstack/react-router'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'

/** 单用户系统：无登录，头像按钮固定跳转设置页。 */
export function ProfileDropdown() {
  return (
    <Button variant='ghost' size='icon' className='rounded-full' asChild>
      <Link to='/bot/settings' aria-label='设置'>
        <Avatar className='h-8 w-8'>
          <AvatarImage src='/avatars/shadcn.jpg' alt='主人' />
          <AvatarFallback>主</AvatarFallback>
        </Avatar>
      </Link>
    </Button>
  )
}
