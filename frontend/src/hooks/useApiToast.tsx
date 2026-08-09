import { createContext, useContext, useRef, type ReactNode } from 'react'
import { Toast } from 'primereact/toast'
import { ApiError } from '../api/errors'
import { t, tError } from '../i18n'

type ToastFns = {
  showError: (error: unknown) => void
  showSuccess: (message: string) => void
}

const ToastContext = createContext<ToastFns | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const toast = useRef<Toast>(null)

  const showError = (error: unknown) => {
    const detail = error instanceof ApiError ? tError(error.errorCode) : tError('INTERNAL_ERROR')
    // resolved on show, not on render: the toast must speak the language of the moment
    toast.current?.show({ severity: 'error', summary: t('common.error'), detail, life: 5000 })
  }
  const showSuccess = (message: string) =>
    toast.current?.show({ severity: 'success', detail: message, life: 2500 })

  return (
    <ToastContext.Provider value={{ showError, showSuccess }}>
      <Toast ref={toast} position="bottom-right" />
      {children}
    </ToastContext.Provider>
  )
}

export function useApiToast(): ToastFns {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useApiToast outside ToastProvider')
  return ctx
}
