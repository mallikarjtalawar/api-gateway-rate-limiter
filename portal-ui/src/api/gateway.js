// Central API client — all backend calls live here
const API_URL = import.meta.env.DEV ? 'http://localhost:8091' : 'https://throttlegate-backend.onrender.com'
const BASE = `${API_URL}/admin`
const getAdminHeaders = () => ({
  'X-Admin-Secret': localStorage.getItem('adminSecret') || '',
  'Content-Type': 'application/json',
})

export async function generateKey(clientName, email) {
  const res = await fetch(`${BASE}/keys`, {
    method: 'POST',
    headers: getAdminHeaders(),
    body: JSON.stringify({ clientName, email }),
  })
  if (res.status === 409) {
    const body = await res.json()
    throw new Error(body.error || 'An API key already exists for this email.')
  }
  if (!res.ok) throw new Error('Failed to generate key')
  return res.json()
}

export async function fetchClients() {
  const res = await fetch(`${BASE}/clients`, { headers: getAdminHeaders() })
  if (!res.ok) throw new Error('Failed to fetch clients')
  return res.json()
}

export async function revokeClientByHash(hashedKey) {
  const res = await fetch(`${BASE}/clients/${hashedKey}`, {
    method: 'DELETE',
    headers: getAdminHeaders(),
  })
  if (!res.ok) throw new Error('Failed to revoke client')
}

export async function rotateKey(hashedKey) {
  const res = await fetch(`${BASE}/keys/${hashedKey}/rotate`, {
    method: 'POST',
    headers: getAdminHeaders(),
  })
  if (!res.ok) throw new Error('Failed to rotate key')
  return res.json()
}

export async function updateClientConfig(hashedKey, config) {
  const res = await fetch(`${BASE}/clients/${hashedKey}/config`, {
    method: 'PUT',
    headers: getAdminHeaders(),
    body: JSON.stringify(config),
  })
  if (!res.ok) throw new Error('Failed to update config')
  return res.json()
}

export async function getClientConfig(hashedKey) {
  const res = await fetch(`${BASE}/clients/${hashedKey}/config`, { headers: getAdminHeaders() })
  if (!res.ok) throw new Error('Failed to fetch config')
  return res.json()
}

export async function fetchUsage(rawKey) {
  const res = await fetch(`${BASE}/usage/${rawKey}`, { headers: getAdminHeaders() })
  if (!res.ok) throw new Error('Failed to fetch usage')
  return res.json()
}

export async function fetchCircuitBreakerStatus() {
  const res = await fetch(`${BASE}/circuit-breaker`, { headers: getAdminHeaders() })
  if (!res.ok) throw new Error('Failed to fetch circuit breaker status')
  return res.json()
}

export async function fetchRpsMetrics() {
  const res = await fetch(`${BASE}/metrics/rps`, { headers: getAdminHeaders() })
  if (!res.ok) throw new Error('Failed to fetch RPS metrics')
  return res.json()
}

// ==========================================
// DEVELOPER API (Public, No Admin Secret)
// ==========================================
const DEV_BASE = `${API_URL}/developer`

export async function developerGenerateKey(clientName, email) {
  const res = await fetch(`${DEV_BASE}/keys`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ clientName, email }),
  })
  if (res.status === 429) {
    throw new Error('Too many registration requests from this IP. Please try again later.')
  }
  if (res.status === 409) {
    const body = await res.json()
    throw new Error(body.error || 'An API key already exists for this email.')
  }
  if (!res.ok) throw new Error('Failed to generate key')
  return res.json()
}

export async function developerFetchUsage(rawKey) {
  const res = await fetch(`${DEV_BASE}/usage/${rawKey}`)
  if (res.status === 404) throw new Error('Key not found or revoked')
  if (!res.ok) throw new Error('Failed to fetch usage')
  return res.json()
}
