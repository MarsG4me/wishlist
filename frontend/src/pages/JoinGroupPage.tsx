import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { joinGroup } from '../api/memberships'
import { ApiError } from '../api/errors'
import { tError } from '../i18n'

/** Invite link landing page: redeems the code and forwards into the group. */
export function JoinGroupPage() {
  const { code } = useParams<{ code: string }>()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const attempted = useRef(false)

  useEffect(() => {
    if (!code || attempted.current) return
    attempted.current = true
    joinGroup(code)
      .then((membership) => navigate(`/groups/${membership.group_id}`, { replace: true }))
      .catch((e) => {
        if (e instanceof ApiError && e.errorCode === 'ALREADY_MEMBER') {
          navigate('/groups', { replace: true })
        } else {
          setError(e instanceof ApiError ? tError(e.errorCode) : tError('INTERNAL_ERROR'))
        }
      })
  }, [code])

  return <div className="app-loading">{error ?? 'Joining…'}</div>
}
