import type { Score, Brand, Panel, AuditResult } from '../types'

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

export function triggerAudit(ws: string, brandId: string, brandName: string, websiteUrl: string): Promise<AuditResult> {
  return fetchJSON(`${BASE}/workspaces/${ws}/audit`, {
    method: 'POST',
    body: JSON.stringify({ brand_id: brandId, brand_name: brandName, website_url: websiteUrl }),
  })
}

export function getNotificationSettings(ws: string) {
  return fetchJSON<import('../types').NotificationSettings>(`${BASE}/workspaces/${ws}/notifications/settings`)
}

export function updateNotificationSettings(ws: string, settings: Partial<import('../types').NotificationSettings>) {
  return fetchJSON<import('../types').NotificationSettings>(`${BASE}/workspaces/${ws}/notifications/settings`, {
    method: 'PUT',
    body: JSON.stringify(settings),
  })
}

export function sendTestEmail(ws: string, email: string) {
  return fetchJSON<{ status: string; to: string }>(`${BASE}/workspaces/${ws}/notifications/test`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export function getRecommendations(ws: string, brandId?: string) {
  const query = brandId ? `?brand_id=${brandId}` : ''
  return fetchJSON<import('../types').Recommendation[]>(`${BASE}/workspaces/${ws}/recommendations${query}`)
}

export function markRecommendationApplied(ws: string, recId: string) {
  return fetchJSON<{ status: string }>(`${BASE}/workspaces/${ws}/recommendations/${recId}/apply`, {
    method: 'POST',
  })
}

export function markRecommendationDismissed(ws: string, recId: string) {
  return fetchJSON<{ status: string }>(`${BASE}/workspaces/${ws}/recommendations/${recId}/dismiss`, {
    method: 'POST',
  })
}

export async function triggerDigest(ws: string): Promise<Blob> {
  const token = localStorage.getItem('token')
  const res = await fetch(`${BASE}/workspaces/${ws}/reports/digest`, {
    method: 'POST',
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }))
    throw new Error(err.error || 'Rapor oluşturulamadı')
  }
  return res.blob()
}
