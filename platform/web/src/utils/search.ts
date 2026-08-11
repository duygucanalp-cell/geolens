// Arama normalizasyonu: küçük harf + aksan işaretlerini ayır (NFD) ve
// birleştirici karakterleri at. Böylece 'İçerik', 'içerik' ve 'icerik'
// aynı sonucu bulur; Türkçe 'İ' gibi büyük/küçük harf farklarına dayanıklıdır.
export function normalizeSearch(s: string): string {
  return s.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
}

export interface TextRange {
  start: number
  end: number
}

// Normalize edilmiş dizideki eşleşmeyi orijinal metindeki karakter aralığına
// geri eşler (İ → i̇ gibi normalizasyon sonrası uzunluk farklarına dayanıklı).
// İlk eşleşmeyi döndürür; eşleşme yoksa null.
export function findHighlightRange(text: string, query: string): TextRange | null {
  const normText = normalizeSearch(text)
  const normQuery = normalizeSearch(query)
  if (!normQuery) return null
  const idx = normText.indexOf(normQuery)
  if (idx === -1) return null
  const normEnd = idx + normQuery.length
  let start: number | null = null
  let end: number | null = null
  let normPos = 0
  for (let i = 0; i < text.length; i++) {
    const chunkLen = normalizeSearch(text[i]).length
    if (start === null && normPos <= idx && idx < normPos + chunkLen) start = i
    // Bitiş: eşleşme aralığı [idx, normEnd) yarı-açıktır; sol taraf sıkıdır
    // (normPos === normEnd'deki karakter eşleşmenin DIŞINDADIR).
    if (normPos < normEnd && normEnd <= normPos + chunkLen) end = i + 1
    normPos += chunkLen
  }
  if (start === null) start = 0
  if (end === null) end = Math.min(start + query.length, text.length)
  return { start, end }
}
