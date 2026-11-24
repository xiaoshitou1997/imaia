import React, { useState } from 'react'
import useDialogState from '@/hooks/use-dialog-state'
import { type Account } from '@/lib/api'

type AccountsDialogType = 'create' | 'edit' | 'delete'

type AccountsContextType = {
  open: AccountsDialogType | null
  setOpen: (str: AccountsDialogType | null) => void
  currentAccount: Account | null
  setCurrentAccount: React.Dispatch<React.SetStateAction<Account | null>>
}

const AccountsContext = React.createContext<AccountsContextType | null>(null)

export function AccountsProvider({ children }: { children: React.ReactNode }) {
  const [open, setOpen] = useDialogState<AccountsDialogType>(null)
  const [currentAccount, setCurrentAccount] = useState<Account | null>(null)

  return (
    <AccountsContext
      value={{ open, setOpen, currentAccount, setCurrentAccount }}
    >
      {children}
    </AccountsContext>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export const useAccounts = () => {
  const context = React.useContext(AccountsContext)

  if (!context) {
    throw new Error('useAccounts has to be used within <AccountsContext>')
  }

  return context
}
