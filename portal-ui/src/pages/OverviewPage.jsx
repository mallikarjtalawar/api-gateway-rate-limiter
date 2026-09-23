import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchClients, fetchCircuitBreakerStatus, fetchRpsMetrics } from '../api/gateway'
import RpsChart from '../components/RpsChart'

export default function OverviewPage() {
  const [clients, setClients] = useState([])
  const [loading, setLoading] = useState(true)
  const [cbStatus, setCbStatus] = useState(null)
  const [rpsHistory, setRpsHistory] = useState([])
  const [simulating, setSimulating] = useState(false)
  const navigate = useNavigate()

  const handleSimulateTraffic = async () => {
    setSimulating(true)
    const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8091'
    const reqs = []
    for (let i = 0; i < 50; i++) {
      // Send dummy traffic without key to generate RPS spike without affecting any specific user's metrics
      reqs.push(fetch(`${API_URL}/test`).catch(() => {}))
    }
    await Promise.all(reqs)
    setTimeout(() => setSimulating(false), 500)
  }

  useEffect(() => {
    Promise.all([
      fetchClients().then(setClients).catch(() => {}),
      fetchCircuitBreakerStatus().then(setCbStatus).catch(() => {})
    ]).finally(() => setLoading(false))

    // Start polling RPS every 1s
    const intervalId = setInterval(() => {
      fetchRpsMetrics().then(setRpsHistory).catch(() => {})
    }, 1000)

    return () => clearInterval(intervalId)
  }, [])

  const total = clients.length
  const active = clients.length // all stored clients are active

  return (
    <>
      <div className="topbar">
        <div>
          <h1>Overview</h1>
          <p>System status and quick summary.</p>
        </div>
        <button className="btn btn-primary" onClick={() => navigate('/dashboard/portal')}>
          + New API Key
        </button>
      </div>

      <div className="stat-grid">
        <div className="stat-card">
          <div className="stat-label">Registered Clients</div>
          <div className="stat-val blue">{loading ? '—' : total}</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">Active Keys</div>
          <div className="stat-val green">{loading ? '—' : active}</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">Gateway Status</div>
          <div className="stat-val green" style={{ fontSize: '1.4rem' }}>● Online</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">Circuit Breaker</div>
          {loading ? (
            <div className="stat-val" style={{ fontSize: '1.4rem' }}>—</div>
          ) : (
            <div className={`stat-val ${cbStatus?.state === 'OPEN' ? 'red' : cbStatus?.state === 'HALF_OPEN' ? 'orange' : 'green'}`} style={{ fontSize: '1.4rem' }}>
              ● {cbStatus?.state || 'UNKNOWN'}
            </div>
          )}
        </div>
      </div>

      <RpsChart rpsHistory={rpsHistory} simulating={simulating} onSimulate={handleSimulateTraffic} />

      <div className="card">
        <h2>Recent Clients</h2>
        {loading ? (
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Loading…</p>
        ) : clients.length === 0 ? (
          <div className="empty-state">
            No clients yet.{' '}
            <span
              style={{ color: 'var(--accent)', cursor: 'pointer' }}
              onClick={() => navigate('/dashboard/portal')}
            >
              Register the first one →
            </span>
          </div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>App Name</th>
                  <th>Email</th>
                  <th>Registered</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {clients.slice(0, 5).map((c, i) => (
                  <tr key={i}>
                    <td style={{ fontWeight: 500 }}>{c.clientName}</td>
                    <td style={{ color: 'var(--text-muted)' }}>{c.email}</td>
                    <td style={{ color: 'var(--text-muted)' }}>
                      {new Date(c.createdAt).toLocaleDateString()}
                    </td>
                    <td>
                      <span className="badge badge-green">● Active</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  )
}
