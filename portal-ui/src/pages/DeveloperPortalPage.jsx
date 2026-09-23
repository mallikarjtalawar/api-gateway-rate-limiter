import { useState } from 'react'
import { developerGenerateKey } from '../api/gateway'
import LandingNavbar from '../components/LandingNavbar'

export default function DeveloperPortalPage() {
  const [clientName, setClientName] = useState('')
  const [email, setEmail] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState(null)
  const [copied, setCopied] = useState(false)
  const [simulating, setSimulating] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!clientName.trim() || !email.trim()) {
      setError('Please fill in all fields.')
      return
    }
    setLoading(true)
    setError('')
    try {
      const data = await developerGenerateKey(clientName.trim(), email.trim())
      setResult(data.apiKey)
      setClientName('')
      setEmail('')
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  const handleCopy = () => {
    navigator.clipboard.writeText(result)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const handleReset = () => {
    setResult(null)
    setCopied(false)
  }

  const handleSimulateTraffic = async () => {
    setSimulating(true)
    const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8091'
    const reqs = []
    for (let i = 0; i < 50; i++) {
      reqs.push(
        fetch(`${API_URL}/api/v1/mock/orders`, {
          headers: { 'X-API-Key': result }
        }).catch(() => {})
      )
    }
    await Promise.all(reqs)
    setTimeout(() => setSimulating(false), 500)
  }

  return (
    <>
      <LandingNavbar />
      <div style={{ padding: '6rem 2rem 2rem', maxWidth: '800px', margin: '0 auto' }}>
        <div className="topbar" style={{ padding: 0, marginBottom: '2rem' }}>
          <div>
            <h1>Developer Portal</h1>
            <p>Register your application and receive an instant API key.</p>
          </div>
        </div>

      <div className="card" style={{ maxWidth: 560 }}>
        {!result ? (
          <>
            <h2>Register Your Application</h2>
            {error && <div className="alert alert-error">{error}</div>}

            <form onSubmit={handleSubmit}>
              <div className="form-row">
                <div className="form-group">
                  <label>Application Name</label>
                  <input
                    type="text"
                    placeholder="e.g. iOS App, Partner Service"
                    value={clientName}
                    onChange={e => setClientName(e.target.value)}
                    required
                  />
                </div>
                <div className="form-group">
                  <label>Developer Email</label>
                  <input
                    type="email"
                    placeholder="you@company.com"
                    value={email}
                    onChange={e => setEmail(e.target.value)}
                    required
                  />
                </div>
              </div>

              <button
                type="submit"
                className="btn btn-primary"
                style={{ width: '100%', marginTop: '0.5rem', justifyContent: 'center' }}
                disabled={loading}
              >
                {loading ? 'Generating…' : '🔑 Generate API Key'}
              </button>
            </form>
          </>
        ) : (
          <>
            <div className="alert alert-success" style={{ marginBottom: '1.25rem' }}>
              ✅ Your API key was generated successfully!
            </div>

            <h2>Your API Key</h2>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '1rem' }}>
              Copy this now — it will <strong>not</strong> be shown again. Store it securely.
            </p>

            <div className="key-result">
              <small>API Key (raw — copy immediately)</small>
              {result}
            </div>

            <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.25rem' }}>
              <button className="btn btn-primary" onClick={handleCopy} style={{ flex: 1, justifyContent: 'center' }}>
                {copied ? '✓ Copied!' : '📋 Copy Key'}
              </button>
              <button className="btn btn-ghost" onClick={handleReset} style={{ flex: 1, justifyContent: 'center' }}>
                + Generate Another
              </button>
            </div>
            
            <div style={{ marginTop: '0.75rem' }}>
              <button 
                className="btn btn-ghost" 
                onClick={handleSimulateTraffic} 
                style={{ width: '100%', justifyContent: 'center', border: '1px solid var(--border)' }}
                disabled={simulating}
              >
                {simulating ? 'Firing Requests...' : '🧪 Simulate Traffic (50 req burst)'}
              </button>
            </div>
          </>
        )}
      </div>

      <div className="card" style={{ maxWidth: 560, marginTop: 0 }}>
        <h2>How to use your key</h2>
        <p style={{ fontSize: '0.875rem', color: 'var(--text-muted)', marginBottom: '1rem' }}>
          Pass the key via the <code style={{ background: 'var(--bg-2)', padding: '0.1rem 0.4rem', borderRadius: 4 }}>X-API-Key</code> header on every request:
        </p>
        <div
          style={{
            background: 'var(--bg-2)',
            border: '1px solid var(--border)',
            borderRadius: 8,
            padding: '1rem',
            fontFamily: 'monospace',
            fontSize: '0.85rem',
            color: 'var(--text-muted)',
            lineHeight: 1.8,
          }}
        >
          curl -H "X-API-Key: YOUR_KEY" \<br />
          &nbsp;&nbsp;&nbsp;&nbsp;http://localhost:8090/your/endpoint
        </div>
      </div>
      </div>
    </>
  )
}
