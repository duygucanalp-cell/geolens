import { useCallback, useState, type Dispatch, type SetStateAction } from 'react'

export interface SharedPageControls<T extends string = string> {
  /** Birleşik sayfanın tek ana filtresi (arama / dönem / marka) */
  value: T
  setValue: Dispatch<SetStateAction<T>>
  /** Artırıldıkça tüm bölümlerin yeniden yüklenmesini tetikler */
  refreshTick: number
  /** Yenileme sayacını artırır (ortak yenile butonu) */
  refresh: () => void
}

// Birleşik sayfaların ortak kontrol deseni: tek ana filtre değeri + bölümler
// arası paylaşılan yenileme sayacı. MergedTracesTab/MergedRegistryTab arama,
// MergedCostsTab dönem, MergedGeoTab/MergedReplayTab marka olarak kullanır.
//
// Not: Başlangıç değeri string literal ise T o literal tipe daralır
// (örn. useSharedPageControls('') → T = ""). Geniş string gerektiren
// durumlarda açık jenerik verin: useSharedPageControls<string>('').
export function useSharedPageControls<T extends string = string>(
  initialValue: T
): SharedPageControls<T> {
  const [value, setValue] = useState<T>(initialValue)
  const [refreshTick, setRefreshTick] = useState(0)
  const refresh = useCallback(() => setRefreshTick(v => v + 1), [])
  return { value, setValue, refreshTick, refresh }
}
