const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8088/api'
const TOKEN_KEY = 'rota_access_token'

export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function saveToken(token) {
  if (token) sessionStorage.setItem(TOKEN_KEY, token)
  else sessionStorage.removeItem(TOKEN_KEY)
}

export async function api(path, options = {}) {
  const headers = { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${API_URL}${path}`, { ...options, headers })
  if (response.status === 204) return null
  const body = await response.json().catch(() => ({}))
  if (!response.ok) {
    const validation = body.errors ? Object.values(body.errors).join(' ') : ''
    throw new Error(validation || body.message || `Não foi possível concluir a operação (HTTP ${response.status} em ${options.method || 'GET'} ${path}).`)
  }
  return body
}

export function jsonBody(value) {
  return JSON.stringify(value)
}
