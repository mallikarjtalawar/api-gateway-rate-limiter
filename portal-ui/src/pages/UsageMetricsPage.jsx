import { useState } from 'react'
import { fetchUsage } from '../api/gateway'

export default function UsageMetricsPage() {
  const [rawKey, setRawKey] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [metrics, setMetrics] = useState(null)

  const handleFetch = async (e) => {
    e.preventDefault()
    if (!rawKey.trim()) return
    setLoading(true)
    setError('')
    setMetrics(null)
    try {
      const data = await fetchUsage(rawKey.trim())
      setMetrics(data)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  // Parse raw metrics from Redis hash
  const total   = metrics ? parseInt(metrics.total          || 0) : 0
  const s200    = metrics ? parseInt(metrics.status_200     || 0) : 0
  const s429    = metrics ? parseInt(metrics.status_429     || 0) : 0
  const s500    = metrics ? parseInt(metrics.status_500     || 0) : 0
  const s503    = metrics ? parseInt(metrics.status_503     || 0) : 0
  const p50Ms   = metrics ? parseInt(metrics.p50_latency_ms || 0) : null
  const p99Ms   = metrics ? parseInt(metrics.p99_latency_ms || 0) : null
  const samples = metrics ? parseInt(metrics.latency_samples  || 0) : 0

  // Extract path breakdown (keys starting with "path_")
  const paths = metrics
    ? Object.entries(metrics)
        .filter(([k]) => k.startsWith('path_'))
        .map(([k, v]) => ({ path: k.replace('path_', ''), count: parseInt(v) }))
        .sort((a, b) => b.count - a.count)
    : []

  const errorCount = s500 + s503
  const otherCount = Math.max(0, total - (s200 + s429 + s500 + s503))
  const successRate = total > 0 ? Math.round((s200 / total) * 100) : 0
  const blockRate   = total > 0 ? Math.round((s429 / total) * 100) : 0

  const exportCsv = () => {
    if (!metrics) return
    const rows = [
      ['Metric', 'Value'],
      ['Total Requests', total],
      ['Success (200)', s200],
      ['Rate Limited (429)', s429],
      ['Internal Error (500)', s500],
      ['Service Unavailable (503)', s503],
      ['Other Status Codes', otherCount],
      ['p50 Latency (ms)', p50Ms || ''],
      ['p99 Latency (ms)', p99Ms || ''],
    ]
    
    if (paths.length > 0) {
      rows.push([])
      rows.push(['Endpoint Path', 'Requests'])
      paths.forEach(p => rows.push([p.path, p.count]))
    }

    const csvContent = rows.map(row => row.join(',')).join('\n')
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `usage_metrics_${rawKey.substring(0, 8)}.csv`)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }

  return (
    <>
      <div className="topbar">
        <div>
          <h1>Usage Metrics</h1>
          <p>Live Redis traffic counters for any API key — this month.</p>
        </div>
      </div>

      {/* Lookup form */}
      <div className="card" style={{ maxWidth: 600 }}>
        <h2>Look Up Key</h2>
        {error && <div className="alert alert-error">{error}</div>}
        <form onSubmit={handleFetch}>
          <div className="form-group">
            <label>Raw API Key</label>
            <input
              type="text"
              placeholder="Paste raw API key here…"
              value={rawKey}
              onChange={e => setRawKey(e.target.value)}
              style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}
            />
          </div>
          <button
            type="submit"
            className="btn btn-primary"
            style={{ marginTop: '0.5rem' }}
            disabled={loading || !rawKey.trim()}
          >
            {loading ? 'Loading…' : '📊 Fetch Metrics'}
          </button>
        </form>
      </div>

      {metrics && total === 0 && (
        <div className="alert alert-error" style={{ maxWidth: 600 }}>
          No usage data found for this key this month.
        </div>
      )}

      {metrics && total > 0 && (
        <>
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '1rem' }}>
            <button className="btn btn-secondary" onClick={exportCsv}>
              📥 Export as CSV
            </button>
          </div>
          {/* Top stats */}
          <div className="stat-grid">
            <div className="stat-card">
              <div className="stat-label">Total Requests</div>
              <div className="stat-val blue">{total.toLocaleString()}</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Success Rate</div>
              <div className="stat-val green">{successRate}%</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">p50 Latency</div>
              <div className="stat-val blue">
                {p50Ms !== null ? `${p50Ms} ms` : '—'}
              </div>
            </div>
          </div>

          {/* Status breakdown */}
          <div className="card">
            <h2>Status Code Breakdown</h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <MetricRow
                label="200 OK — Allowed"
                value={s200}
                max={total}
                color="var(--success)"
              />
              <MetricRow
                label="429 Too Many Requests — Rate Limited"
                value={s429}
                max={total}
                color="var(--danger)"
              />
              {s500 > 0 && (
                <MetricRow
                  label="500 Internal Server Error"
                  value={s500}
                  max={total}
                  color="#f97316"
                />
              )}
              {s503 > 0 && (
                <MetricRow
                  label="503 Service Unavailable — Circuit Breaker"
                  value={s503}
                  max={total}
                  color="#a855f7"
                />
              )}
              {otherCount > 0 && (
                <MetricRow
                  label="Other (e.g. 401 Unauthorized, 404 Not Found)"
                  value={otherCount}
                  max={total}
                  color="var(--text-muted)"
                />
              )}
            </div>
          </div>

          {/* Quick stat pills */}
          <div
            style={{
              display: 'flex',
              gap: '1rem',
              flexWrap: 'wrap',
              marginBottom: '1.5rem',
            }}
          >
            <StatPill label="Blocked" value={`${s429.toLocaleString()} req`} sub={`${blockRate}% of traffic`} color="var(--danger)" />
            <StatPill label="Errors" value={`${errorCount} req`} sub={errorCount === 0 ? 'No errors 🎉' : '500 / 503'} color="#f97316" />
            {p99Ms !== null && (
              <StatPill
                label={samples < 20 ? 'Max Latency' : 'p99 Response'}
                value={samples < 20 ? `${Math.max(p99Ms, p50Ms || 0)} ms` : `${p99Ms} ms`}
                sub={samples < 20 ? `(n=${samples}, too low for p99)` : `across ${samples} proxied req`}
                color={samples < 20 ? 'var(--text-muted)' : 'var(--accent)'}
              />
            )}
          </div>

          {/* Path breakdown */}
          {paths.length > 0 && (
            <div className="card">
              <h2>Endpoint Breakdown</h2>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Path</th>
                      <th>Requests</th>
                      <th>% of Traffic</th>
                      <th>Visual</th>
                    </tr>
                  </thead>
                  <tbody>
                    {paths.map(({ path, count }) => {
                      const pct = Math.round((count / total) * 100)
                      return (
                        <tr key={path}>
                          <td>
                            <code
                              style={{
                                background: 'var(--bg-2)',
                                padding: '0.15rem 0.5rem',
                                borderRadius: 4,
                                fontSize: '0.85rem',
                              }}
                            >
                              {path}
                            </code>
                          </td>
                          <td style={{ fontWeight: 600 }}>{count.toLocaleString()}</td>
                          <td style={{ color: 'var(--text-muted)' }}>{pct}%</td>
                          <td style={{ width: '35%' }}>
                            <div
                              style={{
                                height: 6,
                                background: 'var(--bg-2)',
                                borderRadius: 9999,
                                overflow: 'hidden',
                              }}
                            >
                              <div
                                style={{
                                  height: '100%',
                                  width: `${pct}%`,
                                  background: 'var(--accent)',
                                  borderRadius: 9999,
                                  transition: 'width 0.5s ease',
                                }}
                              />
                            </div>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}
    </>
  )
}

function MetricRow({ label, value, max, color }) {
  const pct = max > 0 ? Math.round((value / max) * 100) : 0
  return (
    <div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          marginBottom: '0.4rem',
          fontSize: '0.875rem',
        }}
      >
        <span style={{ color: 'var(--text-muted)' }}>{label}</span>
        <span style={{ color, fontWeight: 600 }}>
          {value.toLocaleString()} &nbsp;·&nbsp; {pct}%
        </span>
      </div>
      <div
        style={{
          height: 8,
          background: 'var(--bg-2)',
          borderRadius: 9999,
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            height: '100%',
            width: `${pct}%`,
            background: color,
            borderRadius: 9999,
            transition: 'width 0.6s ease',
          }}
        />
      </div>
    </div>
  )
}

function StatPill({ label, value, sub, color }) {
  return (
    <div
      style={{
        background: 'var(--bg-card)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius)',
        padding: '1rem 1.5rem',
        minWidth: 180,
      }}
    >
      <div
        style={{
          fontSize: '0.72rem',
          textTransform: 'uppercase',
          letterSpacing: '0.08em',
          color: 'var(--text-muted)',
          marginBottom: '0.3rem',
          fontWeight: 600,
        }}
      >
        {label}
      </div>
      <div style={{ fontSize: '1.4rem', fontWeight: 800, color }}>{value}</div>
      <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '0.2rem' }}>
        {sub}
      </div>
    </div>
  )
}
