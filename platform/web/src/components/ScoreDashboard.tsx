import { useEffect, useMemo, useState } from 'react'
import { ScoreCard } from './ScoreCard'
import { TrendChart } from './TrendChart'
import { getScores } from '../api/client'
import type { Score } from '../types'

interface ScoreDashboardProps {
  workspaceId: string
}

export function ScoreDashboard({ workspaceId }: ScoreDashboardProps) {
  const [scores, setScores] = useState<Score[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    loadScores()
  }, [workspaceId])

  async function loadScores() {
    try {
      setLoading(true)
      setError(null)
      const data = await getScores(workspaceId)
      setScores(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Skorlar yüklenemedi')
    } finally {
      setLoading(false)
    }
  }

  const scoresByBrand = useMemo(() => {
    const map = new Map<string, Score[]>()
    for (const s of scores) {
      const existing = map.get(s.brand_name) ?? []
      existing.push(s)
      map.set(s.brand_name, existing)
    }
    return map
  }, [scores])

  if (loading) {
    return <div className="dashboard-loading">Skorlar yükleniyor...</div>
  }

  if (error) {
    return (
      <div className="dashboard-error">
        <p>{error}</p>
        <button onClick={loadScores}>Tekrar Dene</button>
      </div>
    )
  }

  if (scores.length === 0) {
    return (
      <div className="dashboard-empty">
        <h2>Henüz skor yok</h2>
        <p>Bir marka ekleyip ölçüm başlatarak görünürlük skorunuzu görebilirsiniz.</p>
      </div>
    )
  }

  return (
    <div className="dashboard">
      <div className="dashboard-header">
        <h2>Görünürlük Skorları</h2>
        <button className="refresh-btn" onClick={loadScores}>
          Yenile
        </button>
      </div>

      {Array.from(scoresByBrand.entries()).map(([brandName, brandScores]) => (
        <div key={brandName} className="brand-section">
          <TrendChart scores={brandScores} brandName={brandName} />
        </div>
      ))}

      <div className="scores-grid">
        {scores.map((score) => (
          <ScoreCard key={score.id} score={score} />
        ))}
      </div>
    </div>
  )
}
