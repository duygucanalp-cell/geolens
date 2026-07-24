import type { Score, Brand, Panel } from '../types'

const BASE = '/v1'

async function fetchJSON<T>(url: string, init?: RequestInit): Promise<T> {
  const token = localStorage.getItem('token')
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }

  const res = await fetch(url, { ...init, headers })
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }))
    throw new Error(err.error || 'API hatası')
  }
  return res.json()
}

export function login(email: string, password: string) {
  return fetchJSON<{ token: string; user_id: string; tenant_id: string; workspace_id: string; role: string }>(
    `${BASE}/auth/login`,
    { method: 'POST', body: JSON.stringify({ email, password }) }
  )
}

export function register(email: string, password: string, name: string) {
  return fetchJSON<{ token: string; user_id: string; tenant_id: string; workspace_id: string; role: string }>(
    `${BASE}/auth/register`,
    { method: 'POST', body: JSON.stringify({ email, password, name }) }
  )
}

export function getScores(ws: string): Promise<Score[]> {
  return fetchJSON(`${BASE}/workspaces/${ws}/scores`)
}

export function getBrands(ws: string): Promise<Brand[]> {
  return fetchJSON(`${BASE}/workspaces/${ws}/brands`)
}

export function getPanels(ws: string): Promise<Panel[]> {
  return fetchJSON(`${BASE}/workspaces/${ws}/panels`)
}

export function triggerMeasurement(ws: string, brandId: string, panelId?: string) {
  return fetchJSON(`${BASE}/workspaces/${ws}/measurements`, {
    method: 'POST',
    body: JSON.stringify({ brand_id: brandId, panel_id: panelId }),
  })
}

export function getScoreHistory(ws: string, brandId: string): Promise<Score[]> {
  return fetchJSON(`${BASE}/workspaces/${ws}/brands/${brandId}/scores`)
}
