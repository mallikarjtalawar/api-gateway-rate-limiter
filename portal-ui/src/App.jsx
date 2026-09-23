import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useState } from 'react'
import { ThemeProvider } from './hooks/useTheme'

import LandingPage       from './pages/LandingPage'
import DashboardLayout   from './components/DashboardLayout'
import OverviewPage      from './pages/OverviewPage'
import ClientRegistryPage from './pages/ClientRegistryPage'
import DeveloperPortalPage from './pages/DeveloperPortalPage'
import UsageMetricsPage  from './pages/UsageMetricsPage'
import DeveloperUsagePage from './pages/DeveloperUsagePage'
import LoginPage         from './pages/LoginPage'

export default function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(
    !!localStorage.getItem('adminSecret')
  )

  const handleLogin = () => setIsAuthenticated(true)
  const handleLogout = () => {
    localStorage.removeItem('adminSecret')
    setIsAuthenticated(false)
  }
  return (
    <ThemeProvider>
      <BrowserRouter>
        <Routes>
          {/* Public */}
          <Route path="/"  element={<LandingPage />} />
          <Route path="/developer" element={<DeveloperPortalPage />} />
          <Route path="/developer/usage" element={<DeveloperUsagePage />} />

          {/* Dashboard (protected layout) */}
          <Route path="/dashboard" element={
            isAuthenticated ? <DashboardLayout onLogout={handleLogout} /> : <LoginPage onLogin={handleLogin} />
          }>
            <Route index           element={<OverviewPage />} />
            <Route path="clients"  element={<ClientRegistryPage />} />
            <Route path="metrics"  element={<UsageMetricsPage />} />
          </Route>

          {/* Fallback */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </ThemeProvider>
  )
}
