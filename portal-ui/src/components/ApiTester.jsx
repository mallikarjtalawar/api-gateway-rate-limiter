import React, { useState } from 'react'

export default function ApiTester({ initialKey = '', onClose }) {
  const [apiKey, setApiKey] = useState(initialKey)
  const [path, setPath] = useState('/posts/1')
  const [method, setMethod] = useState('GET')
  const [response, setResponse] = useState(null)
  const [loading, setLoading] = useState(false)
  const [headers, setHeaders] = useState({})
  const [simulating, setSimulating] = useState(false)
  
  const GATEWAY_URL = import.meta.env.DEV ? 'http://localhost:8091' : 'https://throttlegate-backend.onrender.com'

  const handleSimulate = async () => {
    setSimulating(true)
    const reqs = []
    
    let cleanPath = path
    try {
      if (cleanPath.startsWith('http')) {
        const urlObj = new URL(cleanPath)
        cleanPath = urlObj.pathname + urlObj.search
      }
    } catch (e) {}
    cleanPath = cleanPath.startsWith('/') ? cleanPath : '/' + cleanPath
    
    for (let i = 0; i < 50; i++) {
      reqs.push(
        fetch(`${GATEWAY_URL}${cleanPath}`, {
          method,
          headers: { 'X-API-Key': apiKey }
        }).catch(() => {})
      )
    }
    await Promise.all(reqs)
    setTimeout(() => setSimulating(false), 500)
  }

  const handleSend = async () => {
    setLoading(true)
    
    const startTime = Date.now()
    
    try {
      // Strip domain if user pasted full URL
      let cleanPath = path
      try {
        if (cleanPath.startsWith('http')) {
          const urlObj = new URL(cleanPath)
          cleanPath = urlObj.pathname + urlObj.search
        }
      } catch (e) {
        // Ignore invalid URL
      }
      
      cleanPath = cleanPath.startsWith('/') ? cleanPath : '/' + cleanPath
      
      const res = await fetch(`${GATEWAY_URL}${cleanPath}`, {
        method,
        headers: {
          'X-API-Key': apiKey,
        },
      })
      
      const latency = Date.now() - startTime
      
      const responseHeaders = {}
      res.headers.forEach((value, key) => {
        responseHeaders[key] = value
      })
      
      let body
      const rawText = await res.text()
      try {
        body = JSON.parse(rawText)
      } catch (e) {
        body = rawText
      }
      
      setHeaders(responseHeaders)
      setResponse({
        status: res.status,
        statusText: res.statusText,
        latency,
        body
      })
    } catch (e) {
      setResponse({
        status: 0,
        statusText: 'Network Error / CORS',
        latency: Date.now() - startTime,
        body: e.message
      })
      setHeaders({})
    } finally {
      setLoading(false)
    }
  }

  const getStatusColor = (status) => {
    if (status >= 200 && status < 300) return '#10b981' // green
    if (status === 429) return '#f59e0b' // yellow/orange
    if (status >= 400) return '#ef4444' // red
    return 'var(--text-muted)'
  }

  return (
    <div style={{ padding: '1.5rem', background: 'var(--bg-2)', borderRadius: 'var(--radius)' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <h3 style={{ margin: 0, fontSize: '1.1rem' }}>🧪 Live API Tester</h3>
        {onClose && (
          <button 
            onClick={onClose} 
            style={{ background: 'none', border: 'none', color: 'var(--text-muted)', fontSize: '1.2rem', cursor: 'pointer', padding: '0.2rem', lineHeight: 1 }}
            title="Close"
          >
            ✕
          </button>
        )}
      </div>

      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem' }}>
        <select 
          value={method} 
          onChange={(e) => setMethod(e.target.value)}
          style={{ width: '120px' }}
        >
          <option>GET</option>
          <option>POST</option>
          <option>PUT</option>
          <option>DELETE</option>
        </select>
        <input
          type="text"
          value={path}
          onChange={(e) => setPath(e.target.value)}
          placeholder="/api/resource"
          style={{ flex: 1 }}
        />
        <button className="btn btn-primary" onClick={handleSend} disabled={loading || !apiKey}>
          {loading ? 'Sending...' : 'Send Request'}
        </button>
        <button 
          className="btn btn-ghost" 
          onClick={handleSimulate} 
          disabled={simulating || !apiKey}
          style={{ border: '1px solid var(--border)' }}
          title="Fire 50 concurrent requests"
        >
          {simulating ? 'Firing...' : '🧪 Simulate Traffic'}
        </button>
      </div>

      <div style={{ marginBottom: '1rem' }}>
        <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '0.3rem', textTransform: 'uppercase' }}>
          X-API-Key Header
        </label>
        <input
          type="text"
          value={apiKey}
          onChange={(e) => setApiKey(e.target.value)}
          placeholder="Enter raw API Key"
          style={{ fontFamily: 'monospace' }}
        />
      </div>

      {response && (
        <div style={{ marginTop: '1.5rem', borderTop: '1px solid var(--border)', paddingTop: '1rem', opacity: loading ? 0.6 : 1, transition: 'opacity 0.2s' }}>
          <div style={{ display: 'flex', gap: '1.5rem', marginBottom: '1rem', alignItems: 'center' }}>
            <div>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'block' }}>STATUS</span>
              <span style={{ fontWeight: 700, color: getStatusColor(response.status), fontSize: '1.25rem' }}>
                {response.status} {response.statusText}
              </span>
            </div>
            <div>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'block' }}>TIME</span>
              <span style={{ fontWeight: 600, color: 'var(--text)' }}>
                {response.latency} ms
              </span>
            </div>
          </div>

          <div style={{ display: 'flex', gap: '1.5rem', flexWrap: 'wrap' }}>
            <div style={{ flex: 1, minWidth: '300px' }}>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'block', marginBottom: '0.3rem' }}>RESPONSE BODY</span>
              <pre style={{ background: '#1e1e1e', color: '#d4d4d4', padding: '1rem', borderRadius: '4px', overflowX: 'auto', fontSize: '0.85rem' }}>
                {typeof response.body === 'object' ? JSON.stringify(response.body, null, 2) : response.body}
              </pre>
            </div>
            
            <div style={{ flex: 1, minWidth: '250px' }}>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'block', marginBottom: '0.3rem' }}>RATE LIMIT HEADERS</span>
              <div style={{ background: 'var(--bg)', border: '1px solid var(--border)', borderRadius: '4px', padding: '0.75rem' }}>
                {['x-ratelimit-remaining', 'x-ratelimit-limit', 'x-ratelimit-reset', 'retry-after', 'x-client-tier'].map(h => (
                  headers[h] !== undefined && (
                    <div key={h} style={{ display: 'flex', justifyContent: 'space-between', padding: '0.25rem 0', borderBottom: '1px solid var(--border)', fontSize: '0.85rem' }}>
                      <span style={{ color: 'var(--text-muted)', fontFamily: 'monospace' }}>{h}</span>
                      <span style={{ fontWeight: 600 }}>{headers[h]}</span>
                    </div>
                  )
                ))}
                {!Object.keys(headers).some(k => k.startsWith('x-ratelimit')) && (
                  <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>No rate limit headers returned.</div>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
