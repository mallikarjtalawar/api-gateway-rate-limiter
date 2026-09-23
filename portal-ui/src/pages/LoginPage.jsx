import React, { useState } from 'react'
import { fetchClients } from '../api/gateway'

export default function LoginPage({ onLogin }) {
  const [secret, setSecret] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const handleLogin = async (e) => {
    e.preventDefault()
    if (!secret) return

    setLoading(true)
    setError('')
    
    // Temporarily save to test it
    localStorage.setItem('adminSecret', secret)
    
    try {
      // Test the secret against a real admin endpoint
      await fetchClients()
      onLogin()
    } catch (err) {
      localStorage.removeItem('adminSecret')
      setError('Invalid admin secret. Access denied.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'var(--bg)',
      padding: '2rem'
    }}>
      <div className="card" style={{
        maxWidth: '400px',
        width: '100%',
        animation: 'fadeUp 0.4s ease',
        textAlign: 'center',
        padding: '3rem 2.5rem'
      }}>
        
        <div style={{
          width: '64px',
          height: '64px',
          background: 'rgba(79, 126, 248, 0.1)',
          borderRadius: '50%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          margin: '0 auto 1.5rem',
          fontSize: '1.8rem',
          color: 'var(--accent)'
        }}>
          🔒
        </div>

        <h1 style={{ fontSize: '1.5rem', fontWeight: 800, marginBottom: '0.5rem', letterSpacing: '-0.02em' }}>
          Gateway Admin
        </h1>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '2.5rem' }}>
          Enter the master secret to access the developer dashboard.
        </p>

        {error && <div className="alert alert-error" style={{ marginBottom: '1.5rem', fontSize: '0.85rem' }}>{error}</div>}

        <form onSubmit={handleLogin}>
          <div className="form-group" style={{ textAlign: 'left', marginBottom: '1.5rem' }}>
            <label style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Admin Secret
            </label>
            <input
              type="password"
              value={secret}
              onChange={(e) => setSecret(e.target.value)}
              placeholder="••••••••••••••••"
              style={{
                width: '100%',
                padding: '0.85rem 1rem',
                fontSize: '1rem',
                letterSpacing: '0.1em'
              }}
              autoFocus
            />
          </div>

          <button
            type="submit"
            className="btn btn-primary"
            style={{ width: '100%', justifyContent: 'center', padding: '0.85rem' }}
            disabled={loading || !secret}
          >
            {loading ? 'Verifying...' : 'Unlock Dashboard'}
          </button>
        </form>
      </div>
    </div>
  )
}
