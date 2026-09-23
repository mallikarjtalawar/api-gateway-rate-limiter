import { useState } from 'react'

const WINDOW = 30 // show last 30 seconds

export default function RpsChart({ rpsHistory, simulating, onSimulate }) {
  const [hovered, setHovered] = useState(null)

  // Always show only the last WINDOW seconds — newest at right, oldest at left
  const visible = rpsHistory.slice(-WINDOW)

  const currentRps = visible.length > 0 ? visible[visible.length - 1] : null
  const maxRps     = visible.length > 0 ? Math.max(...visible, 1) : 1
  const peakRps    = visible.length > 0 ? Math.max(...visible) : 0
  const avgRps     = visible.length > 0
    ? Math.round(visible.reduce((a, b) => a + b, 0) / visible.length)
    : 0

  const CHART_H = 120

  return (
    <div className="card" style={{ marginBottom: '2rem' }}>
      {/* Header row */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1rem' }}>
        <div>
          <h2 style={{ margin: 0, marginBottom: '0.35rem' }}>Live Traffic (RPS)</h2>
          <div style={{ display: 'flex', gap: '1.5rem', fontSize: '0.78rem', color: 'var(--text-muted)' }}>
            <span>CURRENT&nbsp;
              <strong style={{ color: currentRps > 0 ? 'var(--accent)' : 'var(--text-muted)', fontSize: '1rem' }}>
                {currentRps !== null ? `${currentRps} req/s` : '—'}
              </strong>
            </span>
            <span>PEAK&nbsp;<strong style={{ color: 'var(--text)' }}>{peakRps} req/s</strong></span>
            <span>AVG&nbsp;<strong style={{ color: 'var(--text)' }}>{avgRps} req/s</strong></span>
          </div>
        </div>
        <button
          className="btn btn-ghost"
          onClick={onSimulate}
          disabled={simulating}
          style={{ border: '1px solid var(--border)', padding: '0.4rem 0.9rem', fontSize: '0.85rem', whiteSpace: 'nowrap' }}
        >
          {simulating ? '⏳ Firing 50 req...' : '🧪 Simulate Traffic'}
        </button>
      </div>

      {/* Chart area */}
      {visible.length === 0 ? (
        <div style={{
          height: CHART_H,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: 'var(--bg)',
          borderRadius: 8,
          border: '1px dashed var(--border)',
          color: 'var(--text-muted)',
          fontSize: '0.9rem',
        }}>
          ⏳ Waiting for traffic data...
        </div>
      ) : (
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          {/* Y-axis */}
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'space-between',
            height: CHART_H,
            fontSize: '0.7rem',
            color: 'var(--text-muted)',
            textAlign: 'right',
            minWidth: 32,
          }}>
            <span>{maxRps}</span>
            <span>{Math.round(maxRps / 2)}</span>
            <span>0</span>
          </div>

          {/* Bars */}
          <div style={{
            flex: 1,
            height: CHART_H,
            display: 'flex',
            alignItems: 'stretch',
            gap: '2px',
            position: 'relative',
            userSelect: 'none',
          }}>
            {/* Grid lines */}
            {[50, 100].map(pct => (
              <div key={pct} style={{
                position: 'absolute',
                left: 0, right: 0,
                bottom: `${pct}%`,
                height: 1,
                background: 'var(--border)',
                opacity: 0.4,
                pointerEvents: 'none',
              }} />
            ))}

            {visible.map((rps, i) => {
              const heightPct = maxRps > 0 ? Math.min(100, Math.max(0, (rps / maxRps) * 100)) : 0
              const isHovered = hovered?.index === i
              // How many seconds ago: rightmost bar (newest) = 1s ago, leftmost = WINDOW s ago
              const secsAgo = visible.length - i
              const timeLabel = secsAgo <= 1 ? 'just now' : `${secsAgo}s ago`

              return (
                <div
                  key={i}
                  style={{ flex: 1, display: 'flex', alignItems: 'flex-end', cursor: 'crosshair', position: 'relative' }}
                  onMouseEnter={() => setHovered({ index: i, rps })}
                  onMouseLeave={() => setHovered(null)}
                >
                  <div
                    style={{
                      width: '100%',
                      height: heightPct > 0 ? `${heightPct}%` : '2px',
                      backgroundColor: isHovered ? '#818cf8' : 'var(--accent)',
                      borderRadius: '2px 2px 0 0',
                      opacity: rps > 0 ? 1 : 0.2,
                      transition: 'background-color 0.1s, height 0.4s ease',
                    }}
                  />
                  {/* Inline floating tooltip */}
                  {isHovered && (
                    <div style={{
                      position: 'absolute',
                      bottom: `calc(${Math.max(heightPct, 4)}% + 8px)`,
                      left: '50%',
                      transform: 'translateX(-50%)',
                      background: '#1e1e2e',
                      border: '1px solid var(--border)',
                      borderRadius: 6,
                      padding: '5px 10px',
                      fontSize: '0.75rem',
                      color: '#fff',
                      whiteSpace: 'nowrap',
                      zIndex: 20,
                      pointerEvents: 'none',
                      boxShadow: '0 4px 16px rgba(0,0,0,0.5)',
                    }}>
                      <strong style={{ color: '#818cf8', fontSize: '0.9rem' }}>{rps}</strong> req/s
                      <br />
                      <span style={{ color: 'var(--text-muted)', fontSize: '0.68rem' }}>{timeLabel}</span>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* X-axis labels */}
      {visible.length > 0 && (
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          marginTop: '0.4rem',
          marginLeft: 40,
          fontSize: '0.7rem',
          color: 'var(--text-muted)',
        }}>
          <span>−{WINDOW}s</span>
          <span>−{Math.round(WINDOW / 2)}s</span>
          <span>Now</span>
        </div>
      )}
    </div>
  )
}
