import { Link, useNavigate } from 'react-router-dom'
import { useTheme } from '../hooks/useTheme'

export default function LandingNavbar() {
  const { theme, toggle } = useTheme()
  const navigate = useNavigate()

  return (
    <nav className="landing-nav">
      <div className="nav-logo">ThrottleGate</div>

      <div className="nav-right">
        <span className="nav-link-plain" onClick={() => navigate('/portal')}>
          Developer Portal
        </span>
        <span className="nav-link-plain" onClick={() => navigate('/dashboard')}>
          Admin
        </span>
        <button className="theme-toggle" onClick={toggle}>
          {theme === 'dark' ? '☀️' : '🌙'}
        </button>
      </div>
    </nav>
  )
}
