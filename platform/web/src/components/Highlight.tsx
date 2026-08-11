import { findHighlightRange } from '../utils/search'

interface Props {
  text: string
  query: string
}

// Arama sorgusuyla eşleşen metin parçasını <mark> ile vurgular (canlı).
// Sorgu boşsa veya eşleşme yoksa metni olduğu gibi döndürür.
export function Highlight({ text, query }: Props) {
  // API'den null/undefined gelebileceği için savunmacı dönüştürme
  const value = text ?? ''
  if (!query.trim()) return <>{value}</>
  const range = findHighlightRange(value, query.trim())
  if (!range) return <>{value}</>
  return (
    <>
      {value.slice(0, range.start)}
      <mark className="search-mark">{value.slice(range.start, range.end)}</mark>
      {value.slice(range.end)}
    </>
  )
}
