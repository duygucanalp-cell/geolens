// PanelSkeleton — veri yüklenirken gösterilen shimmer animasyonlu iskelet görünümü.
// Metin (mesaj) korunur: testler yükleme metnini bekler, erişilebilirlik için
// role="status" + aria-busy ile bildirim yapılır.
interface PanelSkeletonProps {
  message?: string
  compact?: boolean
  rows?: number
}

export function PanelSkeleton({ message, compact, rows = 3 }: PanelSkeletonProps) {
  return (
    <div
      className={`panel-skeleton${compact ? ' panel-skeleton-compact' : ''}`}
      role="status"
      aria-busy="true"
    >
      <div className="skeleton-block skeleton-title" />
      <div className="skeleton-block skeleton-line" />
      <div className="skeleton-block skeleton-line skeleton-line-short" />
      <div className={`skeleton-grid${compact ? ' skeleton-grid-compact' : ''}`}>
        {Array.from({ length: rows }).map((_, i) => (
          <div key={i} className="skeleton-block skeleton-card" />
        ))}
      </div>
      {message && <p className="skeleton-message">{message}</p>}
    </div>
  )
}
