import { NavLink, useNavigate } from 'react-router-dom'
import { useTheme } from '../hooks/useTheme'

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Overview', icon: '⬡' },
  { to: '/dashboard/clients', label: 'Client Registry', icon: '👥' },
  { to: '/dashboard/metrics', label: 'Usage Metrics', icon: '📊' },
]

export default function Sidebar({ onLogout }) {
  const { theme, toggle } = useTheme()
  const navigate = useNavigate()

  return (
    <aside className="sidebar">
      <div
        className="sidebar-logo"
        style={{ cursor: 'pointer' }}
        onClick={() => navigate('/')}
      >
        ThrottleGate
      </div>

      <div className="sidebar-section-label">Navigation</div>

      {NAV_ITEMS.map(({ to, label, icon }) => (
        <NavLink
          key={to}
          to={to}
          end={to === '/dashboard'}
          className={({ isActive }) =>
            `sidebar-item${isActive ? ' active' : ''}`
          }
          style={{ textDecoration: 'none' }}
        >
          <span className="icon">{icon}</span>
          {label}
        </NavLink>
      ))}

      <div className="sidebar-footer">
        <button className="theme-toggle" style={{ width: '100%' }} onClick={toggle}>
          {theme === 'dark' ? '☀️ Light Mode' : '🌙 Dark Mode'}
        </button>
        <button
          className="sidebar-item"
          style={{ marginTop: '0.5rem', color: 'var(--text-muted)' }}
          onClick={() => navigate('/')}
        >
          <span className="icon">←</span> Back to Home
        </button>
        <button
          className="sidebar-item"
          style={{ marginTop: '0.5rem', color: 'var(--danger)' }}
          onClick={onLogout}
        >
          <span className="icon">🔒</span> Lock Dashboard
        </button>
      </div>
    </aside>
  )
}
