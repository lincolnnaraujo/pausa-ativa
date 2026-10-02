import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { guardarUltimaMeta, lerUltimaMeta, metaValida } from '@/preferencias'

describe('preferências', () => {
  beforeEach(() => localStorage.clear())
  afterEach(() => vi.restoreAllMocks())

  it('aceita meta inteira de 1 a 6.000 ml', () => {
    expect(metaValida(1)).toBe(true)
    expect(metaValida(6_000)).toBe(true)
    expect(metaValida(0)).toBe(false)
    expect(metaValida(-1)).toBe(false)
    expect(metaValida(6_001)).toBe(false)
    expect(metaValida(2_500.5)).toBe(false)
    expect(metaValida(Number.NaN)).toBe(false)
  })

  it('lembra a última meta usada e, sem ela, sugere 3.000 ml', () => {
    expect(lerUltimaMeta()).toBe(3_000)

    guardarUltimaMeta(2_500)

    expect(lerUltimaMeta()).toBe(2_500)
  })

  it('ignora uma meta salva que não é válida', () => {
    localStorage.setItem('pausa-ativa.meta-agua-ml', '9000')

    expect(lerUltimaMeta()).toBe(3_000)
  })

  it('funciona sem armazenamento no navegador', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('Bloqueado', 'SecurityError')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('Bloqueado', 'SecurityError')
    })

    expect(() => guardarUltimaMeta(2_500)).not.toThrow()
    expect(lerUltimaMeta()).toBe(3_000)
  })
})
