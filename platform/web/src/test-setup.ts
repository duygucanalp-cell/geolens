import '@testing-library/jest-dom'
import { vi } from 'vitest'
import './i18n'

class ResizeObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}
vi.stubGlobal('ResizeObserver', ResizeObserverMock)
