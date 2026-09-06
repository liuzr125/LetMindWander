const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api'
const TOKEN_KEY = 'zhixing_admin_token'

export function getAdminToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setAdminToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

export async function request(path, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...(getAdminToken() ? { 'X-Admin-Token': getAdminToken() } : {}),
    ...(options.headers || {})
  }
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers })
  const body = await response.json().catch(() => ({}))
  if (!response.ok) {
    const error = new Error(body.message || `请求失败（${response.status}）`)
    error.code = body.code
    error.status = response.status
    if (response.status === 401) window.dispatchEvent(new CustomEvent('admin-unauthorized'))
    throw error
  }
  return body
}
