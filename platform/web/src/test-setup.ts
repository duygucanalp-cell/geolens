import '@testing-library/jest-dom'
import { vi } from 'vitest'
import './i18n'

class ResizeObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}
vi.stubGlobal('ResizeObserver', ResizeObserverMock)

// Bazı jsdom/Node sürümlerinde localStorage/sessionStorage eksik olabiliyor.
// Browser'da her zaman mevcut olan bu depolamaları test ortamı için sağla.
function createStorageMock(): Storage {
  const store = new Map<string, string>()
  return {
    get length() {
      return store.size
    },
    clear: () => store.clear(),
    getItem: (key: string) => store.get(key) ?? null,
    key: (index: number) => Array.from(store.keys())[index] ?? null,
    removeItem: (key: string) => { store.delete(key) },
    setItem: (key: string, value: string) => { store.set(key, String(value)) },
  }
}

if (typeof window !== 'undefined') {
  if (!window.localStorage) {
    const mock = createStorageMock()
    Object.defineProperty(window, 'localStorage', { value: mock, configurable: true })
    vi.stubGlobal('localStorage', mock)
  }
  if (!window.sessionStorage) {
    const mock = createStorageMock()
    Object.defineProperty(window, 'sessionStorage', { value: mock, configurable: true })
    vi.stubGlobal('sessionStorage', mock)
  }
}
