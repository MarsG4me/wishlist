import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Button } from 'primereact/button'
import { t } from '../i18n'

/** Catches render errors below it and shows a friendly fallback with a reload button. */
export default class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error(error, info.componentStack)
  }

  render() {
    if (!this.state.failed) return this.props.children
    return (
      <div className="app-loading" role="alert" style={{ flexDirection: 'column', gap: '1rem' }}>
        <h2>{t('error.title')}</h2>
        <p>{t('error.body')}</p>
        <Button label={t('error.reload')} icon="pi pi-refresh" onClick={() => window.location.reload()} />
      </div>
    )
  }
}
