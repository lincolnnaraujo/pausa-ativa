import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'

import { umaJornada, umMarco } from '@/__tests__/fabrica'
import { EventSourceFalso, instalarNavegadorFalso } from '@/__tests__/navegador'

import { useEventos } from '../useEventos'

const ouvintes = {
  aoDispararMarco: vi.fn(),
  aoAtualizarJornada: vi.fn(),
  aoConectar: vi.fn(),
}

let escopo: ReturnType<typeof effectScope>
function criar() {
  escopo = effectScope()
  return escopo.run(() => useEventos(ouvintes))!
}

/** Faz a conexão atual falhar de vez e devolve quantas existem depois de `ms`. */
function desistirEEsperar(ms: number) {
  EventSourceFalso.ultima().cair({ desistir: true })
  vi.advanceTimersByTime(ms)
  return EventSourceFalso.instancias.length
}

describe('useEventos', () => {
  beforeEach(() => {
    instalarNavegadorFalso()
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    vi.clearAllMocks()
  })

  afterEach(() => {
    escopo.stop()
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('conecta ao stream de eventos e avisa quando a conexão abre', () => {
    const { estado } = criar()
    const fonte = EventSourceFalso.ultima()
    expect(fonte.url).toBe('/api/v1/eventos')
    expect(estado.value).toBe('conectando')

    fonte.abrir()

    expect(estado.value).toBe('conectada')
    expect(ouvintes.aoConectar).toHaveBeenCalledWith({ reconexao: false })
  })

  it('entrega marco-disparado e jornada-atualizada já convertidos de JSON', () => {
    criar()
    const fonte = EventSourceFalso.ultima()
    const marco = umMarco(1, { status: 'PENDENTE' })
    const jornada = umaJornada()

    fonte.emitir('marco-disparado', marco)
    fonte.emitir('jornada-atualizada', jornada)

    expect(ouvintes.aoDispararMarco).toHaveBeenCalledWith(marco)
    expect(ouvintes.aoAtualizarJornada).toHaveBeenCalledWith(jornada)
  })

  it('ignora um evento malformado', () => {
    criar()

    EventSourceFalso.ultima().emitir('jornada-atualizada', '{')

    expect(ouvintes.aoAtualizarJornada).not.toHaveBeenCalled()
  })

  it('na queda, fica reconectando e deixa o navegador reconectar sozinho', () => {
    const { estado } = criar()
    const fonte = EventSourceFalso.ultima()
    fonte.abrir()

    fonte.cair()
    expect(estado.value).toBe('reconectando')
    vi.advanceTimersByTime(60_000)
    expect(EventSourceFalso.instancias).toHaveLength(1)

    fonte.abrir()
    expect(estado.value).toBe('conectada')
    expect(ouvintes.aoConectar).toHaveBeenLastCalledWith({ reconexao: true })
  })

  it('quando o navegador desiste (502 do nginx), abre outra conexão com espera crescente', () => {
    const { estado } = criar()

    expect(desistirEEsperar(1_999)).toBe(1)
    expect(estado.value).toBe('reconectando')
    vi.advanceTimersByTime(1)
    expect(EventSourceFalso.instancias).toHaveLength(2)

    expect(desistirEEsperar(3_999)).toBe(2)
    vi.advanceTimersByTime(1)
    expect(EventSourceFalso.instancias).toHaveLength(3)

    EventSourceFalso.ultima().abrir()
    expect(estado.value).toBe('conectada')
    expect(ouvintes.aoConectar).toHaveBeenCalledWith({ reconexao: true })

    // Conectou: a espera volta ao início.
    expect(desistirEEsperar(2_000)).toBe(4)
  })

  it('espera no máximo 30 s entre as tentativas', () => {
    criar()
    for (const espera of [2_000, 4_000, 8_000, 16_000]) {
      desistirEEsperar(espera)
    }
    const antes = EventSourceFalso.instancias.length

    expect(desistirEEsperar(30_000)).toBe(antes + 1)
    expect(desistirEEsperar(29_999)).toBe(antes + 1)
    vi.advanceTimersByTime(1)
    expect(EventSourceFalso.instancias).toHaveLength(antes + 2)
  })

  it('fecha a conexão e cancela a reconexão quando a tela é desmontada', () => {
    criar()
    const fonte = EventSourceFalso.ultima()
    fonte.cair({ desistir: true })

    escopo.stop()
    vi.advanceTimersByTime(60_000)

    expect(fonte.fechada).toBe(true)
    expect(EventSourceFalso.instancias).toHaveLength(1)
  })
})
