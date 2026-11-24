import { Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useTasks } from './tasks-provider'

export function TasksPrimaryButtons() {
  const { setOpen } = useTasks()
  return (
    <Button className='space-x-1' onClick={() => setOpen('create')}>
      <span>新建任务</span> <Plus size={18} />
    </Button>
  )
}
