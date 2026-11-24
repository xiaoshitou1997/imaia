import React, { useState } from 'react'
import useDialogState from '@/hooks/use-dialog-state'
import { type Provider } from '@/lib/api'

type ProvidersDialogType = 'create' | 'edit' | 'models' | 'delete'

type ProvidersContextType = {
  open: ProvidersDialogType | null
  setOpen: (str: ProvidersDialogType | null) => void
  currentProvider: Provider | null
  setCurrentProvider: React.Dispatch<React.SetStateAction<Provider | null>>
}

const ProvidersContext = React.createContext<ProvidersContextType | null>(null)

export function ProvidersProvider({ children }: { children: React.ReactNode }) {
  const [open, setOpen] = useDialogState<ProvidersDialogType>(null)
  const [currentProvider, setCurrentProvider] = useState<Provider | null>(null)

  return (
    <ProvidersContext
      value={{ open, setOpen, currentProvider, setCurrentProvider }}
    >
      {children}
    </ProvidersContext>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export const useProviders = () => {
  const context = React.useContext(ProvidersContext)

  if (!context) {
    throw new Error('useProviders has to be used within <ProvidersContext>')
  }

  return context
}
