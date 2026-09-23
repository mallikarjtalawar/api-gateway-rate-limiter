import { useNavigate } from 'react-router-dom'
import LandingNavbar from '../components/LandingNavbar'

const FEATURES = [
  {
    icon: '⚡',
    title: 'Sub-millisecond Rate Limiting',
    desc: 'Sliding-window counters backed by Redis. Zero latency impact on your critical path.',
  },
  {
    icon: '🛡️',
    title: 'Circuit Breaker Protection',
    desc: 'Powered by Resilience4j. Automatically trips when your upstream is struggling.',
  },
  {
    icon: '📊',
    title: 'Live Usage Metrics',
    desc: 'Async event-driven counters track every request. View real-time traffic breakdowns.',
  },
  {
    icon: '🔑',
    title: 'Self-Service API Keys',
    desc: 'Developers register instantly. Cryptographically secure 32-byte keys, stored as SHA-256 hashes.',
  },
  {
    icon: '👥',
    title: 'Client Registry',
    desc: 'Every key is tied to a named client. View, monitor, and revoke access globally in one click.',
  },
  {
    icon: '🌐',
    title: 'Proxy Routing',
    desc: 'Transparent reverse proxy with hop-by-hop header stripping and configurable upstream URLs.',
  },
]

export default function LandingPage() {
  const navigate = useNavigate()

  return (
    <>
      <LandingNavbar />

      {/* Hero */}
      <section className="hero">
        <div className="hero-badge">
          <span>●</span> Live — Spring Boot + Redis + React
        </div>

        <h1>
          The API Gateway for{' '}
          <span className="gradient-word">Modern Teams</span>
        </h1>

        <p>
          Secure, high-performance API routing with real-time rate limiting,
          circuit breaking, and a self-service developer portal — all in one place.
        </p>

        <div className="hero-actions">
          <button
            className="btn btn-primary btn-lg"
            onClick={() => navigate('/developer')}
          >
            🚀 Get an API Key
          </button>
          <button
            className="btn btn-secondary btn-lg"
            onClick={() => navigate('/developer/usage')}
          >
            📊 View My Usage
          </button>
          <button
            className="btn btn-ghost btn-lg"
            onClick={() => navigate('/dashboard')}
          >
            Admin Login →
          </button>
        </div>
      </section>

      {/* Features */}
      <section className="features">
        <div className="features-header">
          <p>Everything you need</p>
          <h2>
            Built for reliability{' '}
            <span>and developer experience.</span>
          </h2>
        </div>

        <div className="feature-grid">
          {FEATURES.map(({ icon, title, desc }) => (
            <div className="feature-card" key={title}>
              <div className="feature-icon">{icon}</div>
              <h3>{title}</h3>
              <p>{desc}</p>
            </div>
          ))}
        </div>
      </section>
    </>
  )
}
