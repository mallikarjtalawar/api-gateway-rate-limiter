import { Fragment, useEffect, useState } from 'react'
import { fetchClients, revokeClientByHash, updateClientConfig, rotateKey } from '../api/gateway'
import ApiTester from '../components/ApiTester'

const TIERS = [
  { id: 'free',       label: 'Free',       limit: 10,   window: 60 },
  { id: 'pro',        label: 'Pro',        limit: 100,  window: 60 },
  { id: 'enterprise', label: 'Enterprise', limit: 1000, window: 60 },
  { id: 'custom',     label: 'Custom',     limit: null, window: null },
]

const TIER_COLORS = {
  free:       { bg: 'rgba(107,114,153,0.12)', color: '#6b7a99' },
  pro:        { bg: 'rgba(79,126,248,0.12)',  color: 'var(--accent)' },
  enterprise: { bg: 'rgba(124,58,237,0.12)', color: '#a855f7' },
  custom:     { bg: 'rgba(249,115,22,0.12)', color: '#f97316' },
}

export default function ClientRegistryPage() {
  const [clients,   setClients]   = useState([])
  const [loading,   setLoading]   = useState(true)
  const [error,     setError]     = useState('')
  const [revoking,  setRevoking]  = useState(null)
  const [expanded,  setExpanded]  = useState(null) // hashedKey of expanded row
  const [showTester, setShowTester] = useState(false)
  const [rotating,  setRotating]  = useState(null)
  const [rotatedKey, setRotatedKey] = useState(null)

  const [confirmAction, setConfirmAction] = useState(null)

  const load = () => {
    setLoading(true)
    fetchClients()
      .then(setClients)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  const executeRevoke = async (hashedKey) => {
    setRevoking(hashedKey)
    try {
      await revokeClientByHash(hashedKey)
      setClients(prev => prev.filter(c => c.hashedKey !== hashedKey))
      if (expanded === hashedKey) setExpanded(null)
    } catch (e) {
      setError(e.message)
    } finally {
      setRevoking(null)
    }
  }

  const handleRevoke = (hashedKey) => {
    setConfirmAction({
      title: 'Revoke API Key',
      message: 'Revoke this client API key? This is instant and irreversible.',
      confirmLabel: 'Revoke Key',
      isDanger: true,
      onConfirm: () => executeRevoke(hashedKey)
    })
  }

  const executeRotate = async (hashedKey) => {
    setRotating(hashedKey)
    try {
      const data = await rotateKey(hashedKey)
      setRotatedKey(data.apiKey)
      // Refresh the client list since a new hashed key was created
      load()
    } catch (e) {
      setError(e.message)
    } finally {
      setRotating(null)
    }
  }

  const handleRotate = (hashedKey) => {
    setConfirmAction({
      title: 'Rotate API Key',
      message: "Rotate this client's API key? The old key will work for 24 hours, but the new key will be generated now.",
      confirmLabel: 'Rotate Key',
      isDanger: false,
      onConfirm: () => executeRotate(hashedKey)
    })
  }

  const toggleExpand = (hashedKey) => {
    setExpanded(prev => prev === hashedKey ? null : hashedKey)
  }

  return (
    <>
      <div className="topbar">
        <div>
          <h1>Client Registry</h1>
          <p>All registered developers, tiers, and rate limit configuration.</p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button className="btn btn-secondary" onClick={() => setShowTester(!showTester)}>
            {showTester ? '✕ Close Tester' : '🧪 API Tester'}
          </button>
          <button className="btn btn-ghost" onClick={load}>↻ Refresh</button>
        </div>
      </div>

      {showTester && (
        <div className="modal-overlay" onClick={() => setShowTester(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <ApiTester onClose={() => setShowTester(false)} />
          </div>
        </div>
      )}

      {rotatedKey && (
        <div className="modal-overlay" onClick={() => setRotatedKey(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ padding: '2rem', maxWidth: '500px' }}>
            <h2 style={{ marginTop: 0 }}>Key Rotated Successfully</h2>
            <div className="alert alert-success" style={{ marginBottom: '1rem' }}>
              The old key will expire in 24 hours. Here is the new key.
            </div>
            <div className="key-result" style={{ marginBottom: '1.5rem' }}>
              <small>New API Key (raw — copy immediately)</small>
              {rotatedKey}
            </div>
            <button className="btn btn-primary" onClick={() => {
              navigator.clipboard.writeText(rotatedKey)
              alert('Copied to clipboard!')
            }} style={{ width: '100%', justifyContent: 'center' }}>
              📋 Copy Key & Close
            </button>
            <button className="btn btn-ghost" onClick={() => setRotatedKey(null)} style={{ width: '100%', justifyContent: 'center', marginTop: '0.5rem' }}>
              Dismiss
            </button>
          </div>
        </div>
      )}

      {confirmAction && (
        <div className="modal-overlay" onClick={() => setConfirmAction(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ padding: '2rem', maxWidth: '400px' }}>
            <h3 style={{ marginTop: 0, marginBottom: '1rem' }}>{confirmAction.title}</h3>
            <p style={{ color: 'var(--text-muted)', marginBottom: '2rem', lineHeight: '1.5' }}>
              {confirmAction.message}
            </p>
            <div style={{ display: 'flex', gap: '1rem', justifyContent: 'flex-end' }}>
              <button className="btn btn-ghost" onClick={() => setConfirmAction(null)}>
                Cancel
              </button>
              <button 
                className={confirmAction.isDanger ? "btn btn-danger" : "btn btn-primary"}
                onClick={() => {
                  setConfirmAction(null)
                  confirmAction.onConfirm()
                }}
              >
                {confirmAction.confirmLabel}
              </button>
            </div>
          </div>
        </div>
      )}

      {error && <div className="alert alert-error">{error}</div>}

      <div className="card">
        {loading ? (
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Loading clients…</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>App Name</th>
                  <th>Developer Email</th>
                  <th>Registered</th>
                  <th>Tier</th>
                  <th>Rate Limit</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {clients.map((c) => (
                  <Fragment key={c.hashedKey}>
                    <tr key={c.hashedKey}>
                      <td style={{ fontWeight: 600 }}>{c.clientName}</td>
                      <td style={{ color: 'var(--text-muted)' }}>{c.email}</td>
                      <td style={{ color: 'var(--text-muted)', fontSize: '0.82rem' }}>
                        {new Date(c.createdAt).toLocaleDateString()}
                      </td>
                      <td>
                        <TierBadge tier={c.tier || 'free'} />
                      </td>
                      <td style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                        {c.rateLimit || '10'} req / {c.windowSecs || '60'}s
                      </td>
                      <td>
                        <div style={{ display: 'flex', gap: '0.5rem' }}>
                          <button
                            className="btn btn-ghost"
                            style={{ padding: '0.3rem 0.75rem', fontSize: '0.8rem' }}
                            onClick={() => toggleExpand(c.hashedKey)}
                          >
                            {expanded === c.hashedKey ? '▲ Close' : '⚙ Config'}
                          </button>
                          <button
                            className="btn btn-ghost"
                            style={{ padding: '0.3rem 0.75rem', fontSize: '0.8rem', color: '#f59e0b', borderColor: '#f59e0b33' }}
                            disabled={rotating === c.hashedKey}
                            onClick={() => handleRotate(c.hashedKey)}
                          >
                            {rotating === c.hashedKey ? '…' : 'Rotate'}
                          </button>
                          <button
                            className="btn btn-danger"
                            style={{ padding: '0.3rem 0.75rem', fontSize: '0.8rem' }}
                            disabled={revoking === c.hashedKey}
                            onClick={() => handleRevoke(c.hashedKey)}
                          >
                            {revoking === c.hashedKey ? '…' : 'Revoke'}
                          </button>
                        </div>
                      </td>
                    </tr>

                    {expanded === c.hashedKey && (
                      <tr key={`${c.hashedKey}-config`}>
                        <td colSpan="6" style={{ padding: 0 }}>
                          <ConfigPanel
                            client={c}
                            onSaved={(updated) => {
                              setClients(prev =>
                                prev.map(x =>
                                  x.hashedKey === c.hashedKey ? { ...x, ...updated } : x
                                )
                              )
                              setExpanded(null)
                            }}
                          />
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}

                {clients.length === 0 && (
                  <tr>
                    <td colSpan="6">
                      <div className="empty-state">No clients registered yet.</div>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  )
}

/* ─── Tier Badge ─────────────────────────────────────────────── */
function TierBadge({ tier }) {
  const style = TIER_COLORS[tier] || TIER_COLORS.free
  return (
    <span
      style={{
        background: style.bg,
        color: style.color,
        padding: '0.2rem 0.6rem',
        borderRadius: 9999,
        fontSize: '0.75rem',
        fontWeight: 700,
        textTransform: 'capitalize',
      }}
    >
      {tier}
    </span>
  )
}

/* ─── Inline Config Panel ────────────────────────────────────── */
function ConfigPanel({ client, onSaved }) {
  const [tier,       setTier]       = useState(client.tier || 'free')
  const [rateLimit,  setRateLimit]  = useState(client.rateLimit  || '')
  const [windowSecs, setWindowSecs] = useState(client.windowSecs || '')
  const [saving,     setSaving]     = useState(false)
  const [err,        setErr]        = useState('')

  const selectedPreset = TIERS.find(t => t.id === tier)
  const isCustom = tier === 'custom'

  const handleTierChange = (newTier) => {
    setTier(newTier)
    const preset = TIERS.find(t => t.id === newTier)
    if (preset && preset.limit !== null) {
      setRateLimit(String(preset.limit))
      setWindowSecs(String(preset.window))
    }
  }

  const handleSave = async () => {
    setSaving(true)
    setErr('')
    try {
      await updateClientConfig(client.hashedKey, {
        tier,
        rateLimit: parseInt(rateLimit) || selectedPreset?.limit || 10,
        windowSecs: parseInt(windowSecs) || 60,
      })
      onSaved({
        tier,
        rateLimit: rateLimit || String(selectedPreset?.limit || 10),
        windowSecs: windowSecs || '60',
      })
    } catch (e) {
      setErr(e.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div
      style={{
        background: 'var(--bg-2)',
        borderTop: '1px solid var(--border)',
        padding: '1.5rem 2rem',
        animation: 'fadeUp 0.2s ease',
      }}
    >
      <div style={{ fontWeight: 600, marginBottom: '1.25rem', fontSize: '0.9rem' }}>
        ⚙️ Configure Rate Limits — <span style={{ color: 'var(--text-muted)' }}>{client.clientName}</span>
      </div>

      {err && <div className="alert alert-error" style={{ marginBottom: '1rem' }}>{err}</div>}

      {/* Tier selector pills */}
      <div style={{ marginBottom: '1.25rem' }}>
        <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginBottom: '0.6rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.07em' }}>
          Tier
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
          {TIERS.map(t => (
            <button
              key={t.id}
              onClick={() => handleTierChange(t.id)}
              style={{
                padding: '0.45rem 1.1rem',
                borderRadius: 9999,
                border: `1px solid ${tier === t.id ? TIER_COLORS[t.id].color : 'var(--border)'}`,
                background: tier === t.id ? TIER_COLORS[t.id].bg : 'transparent',
                color: tier === t.id ? TIER_COLORS[t.id].color : 'var(--text-muted)',
                cursor: 'pointer',
                fontWeight: 600,
                fontSize: '0.85rem',
                fontFamily: 'Inter, sans-serif',
                transition: 'all 0.15s',
              }}
            >
              {t.label}
              {t.limit && (
                <span style={{ marginLeft: '0.4rem', opacity: 0.7, fontSize: '0.75rem' }}>
                  ({t.limit}/min)
                </span>
              )}
            </button>
          ))}
        </div>
      </div>

      {/* Custom inputs */}
      <div style={{ display: 'flex', gap: '1rem', alignItems: 'flex-end' }}>
        <div className="form-group" style={{ margin: 0, flex: 1 }}>
          <label style={{ fontSize: '0.78rem', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.07em' }}>
            Requests per Window
          </label>
          <input
            type="number"
            min="1"
            value={rateLimit}
            onChange={e => { setRateLimit(e.target.value); setTier('custom') }}
            placeholder={isCustom ? 'e.g. 50' : String(selectedPreset?.limit || 10)}
            disabled={!isCustom}
            style={{ opacity: isCustom ? 1 : 0.5 }}
          />
        </div>
        <div className="form-group" style={{ margin: 0, flex: 1 }}>
          <label style={{ fontSize: '0.78rem', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.07em' }}>
            Window (seconds)
          </label>
          <input
            type="number"
            min="1"
            value={windowSecs}
            onChange={e => { setWindowSecs(e.target.value); setTier('custom') }}
            placeholder={isCustom ? 'e.g. 30' : '60'}
            disabled={!isCustom}
            style={{ opacity: isCustom ? 1 : 0.5 }}
          />
        </div>
        <button
          className="btn btn-primary"
          onClick={handleSave}
          disabled={saving}
          style={{ marginBottom: '0.05rem' }}
        >
          {saving ? 'Saving…' : '✓ Save Config'}
        </button>
      </div>

      <p style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '0.9rem' }}>
        Changes propagate in ≤5 seconds. No gateway restart required.
      </p>
    </div>
  )
}
