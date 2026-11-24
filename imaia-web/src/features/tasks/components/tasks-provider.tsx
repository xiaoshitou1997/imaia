import React, { useState } from 'react'
import useDialogState from '@/hooks/use-dialog-state'
import { type TaskRow } from '@/lib/api'

type TasksDialogType = 'create' | 'runs' | 'cancel'

type TasksContextType = {
  open: TasksDialogType | null
  setOpen: (str: TasksDialogType | null) => void
  currentTask: TaskRow | null
  setCurrentTask: React.Dispatch<React.SetStateAction<TaskRow | null>>
}

const TasksContext = React.createContext<TasksContextType | null>(null)

export function TasksProvider({ children }: { children: React.ReactNode }) {
  const [open, setOpen] = useDialogState<TasksDialogType>(null)
  const [currentTask, setCurrentTask] = useState<TaskRow | null>(null)

  return (
    <TasksContext value={{ open, setOpen, currentTask, setCurrentTask }}>
      {children}
    </TasksContext>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export const useTasks = () => {
  const tasksContext = React.useContext(TasksContext)

  if (!tasksContext) {
    throw new Error('useTasks has to be used within <TasksContext>')
  }

  return tasksContext
}
