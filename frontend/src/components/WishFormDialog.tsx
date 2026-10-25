import { useEffect, useState } from 'react'
import { Dialog } from 'primereact/dialog'
import { InputText } from 'primereact/inputtext'
import { InputTextarea } from 'primereact/inputtextarea'
import { Button } from 'primereact/button'
import { Checkbox } from 'primereact/checkbox'
import type { OwnWish, WishLink } from '../api/types'
import type { WishInput } from '../api/wishes'
import { useT } from '../i18n'

interface Props {
  visible: boolean
  wish: OwnWish | null // null = create
  onSave: (input: WishInput) => Promise<void>
  onHide: () => void
}

/** Trims and prefixes https:// unless a scheme:// or a known non-http scheme (kept, then rejected) leads. */
const normalizeUrl = (raw: string): string => {
  const url = raw.trim()
  const hasScheme = /^[a-z][a-z0-9+.-]*:\/\//i.test(url) || /^(javascript|data|mailto|tel|sms|file|vbscript|blob):/i.test(url)
  return hasScheme ? url : `https://${url}`
}

/** Returns an i18n error for a non-empty link that is not an absolute http(s) URL <= 2048 chars. */
const linkError = (raw: string, t: ReturnType<typeof useT>): string | null => {
  if (raw.trim() === '') return null
  const url = normalizeUrl(raw)
  if (url.length > 2048) return t('wishes.linkTooLong')
  try {
    const { protocol } = new URL(url)
    return protocol === 'http:' || protocol === 'https:' ? null : t('wishes.linkInvalid')
  } catch {
    return t('wishes.linkInvalid')
  }
}

export function WishFormDialog({ visible, wish, onSave, onHide }: Props) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  // a wish is giftable once or unlimited — pooling for one gift is done via open claims
  const [unlimited, setUnlimited] = useState(false)
  const [links, setLinks] = useState<WishLink[]>([])
  const [saving, setSaving] = useState(false)
  const t = useT()

  useEffect(() => {
    if (!visible) return
    setName(wish?.name ?? '')
    setDescription(wish?.description ?? '')
    setUnlimited(wish ? wish.quantity === null : false)
    setLinks(wish?.links ?? [])
  }, [visible, wish])

  const setLink = (index: number, patch: Partial<WishLink>) =>
    setLinks(links.map((link, i) => (i === index ? { ...link, ...patch } : link)))

  const linkErrors = links.map((link) => linkError(link.url, t))
  const invalid = linkErrors.some(Boolean)

  const save = async () => {
    if (invalid) return
    setSaving(true)
    try {
      await onSave({
        name,
        description: description || null,
        quantity: unlimited ? null : 1,
        links: links
          .filter((link) => link.url.trim() !== '')
          .map((link) => ({ ...link, url: normalizeUrl(link.url) })),
      })
      onHide()
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog
      header={wish ? t('wishes.editHeader') : t('wishes.newHeader')}
      visible={visible}
      style={{ width: 'min(480px, 95vw)' }}
      onHide={onHide}
    >
      <div className="form-field">
        <label htmlFor="wish-name">{t('common.name')}</label>
        <InputText id="wish-name" value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
      </div>
      <div className="form-field">
        <label htmlFor="wish-desc">{t('common.description')}</label>
        <InputTextarea
          id="wish-desc"
          value={description}
          maxLength={500}
          rows={3}
          onChange={(e) => setDescription(e.target.value)}
        />
      </div>
      <div className="form-field">
        <label>
          <Checkbox checked={unlimited} onChange={(e) => setUnlimited(!!e.checked)} />{' '}
          {t('wishes.giftableUnlimited')}
        </label>
        <small className="muted">{t('wishes.quantityHint')}</small>
      </div>
      <div className="form-field">
        <label>{t('wishes.links')}</label>
        {links.map((link, i) => (
          <div className="link-editor-row" key={i} style={{ flexWrap: 'wrap' }}>
            <InputText
              type="url"
              inputMode="url"
              placeholder="https://…"
              value={link.url}
              invalid={!!linkErrors[i]}
              onChange={(e) => setLink(i, { url: e.target.value })}
            />
            <InputText
              placeholder={t('wishes.linkLabel')}
              value={link.label ?? ''}
              onChange={(e) => setLink(i, { label: e.target.value || null })}
            />
            <Button
              icon="pi pi-trash"
              severity="danger"
              text
              onClick={() => setLinks(links.filter((_, j) => j !== i))}
            />
            {linkErrors[i] && <small className="p-error" style={{ flexBasis: '100%' }}>{linkErrors[i]}</small>}
          </div>
        ))}
        <Button
          label={t('wishes.addLink')}
          icon="pi pi-plus"
          text
          disabled={links.length >= 20}
          onClick={() => setLinks([...links, { url: '', label: null }])}
        />
      </div>
      <Button
        label={t('common.save')}
        icon="pi pi-check"
        disabled={!name.trim() || saving || invalid}
        onClick={save}
      />
    </Dialog>
  )
}
