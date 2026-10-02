import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick } from 'vue'

import { AudioContextFalso, instalarNavegadorFalso, tonsTocados } from '@/__tests__/navegador'

import { useSom } from '../useSom'

let escopo: ReturnType<typeof effectScope>
function criar() {
  escopo = effectScope()
  return escopo.run(useSom)!
}

describe('useSom', () => {
  beforeEach(() => {
    instalarNavegadorFalso()
    localStorage.clear()
  })

  afterEach(() => {
    escopo.stop()
    vi.unstubAllGlobals()
  })

  it('começa ligado e toca um tom curto de 880 Hz', () => {
    const { ligado, tocar } = criar()
    expect(ligado.value).toBe(true)

    tocar()

    const [tom] = tonsTocados()
    expect(tom!.type).toBe('sine')
    expect(tom!.frequency.value).toBe(880)
    expect(tom!.start).toHaveBeenCalledWith(10)
    expect(tom!.stop).toHaveBeenCalledWith(10.35)
  })

  it('usa um contexto de áudio só e o retoma se o navegador o suspendeu', () => {
    const { tocar } = criar()
    tocar()
    const [contexto] = AudioContextFalso.criados
    contexto!.state = 'suspended'

    tocar()

    expect(AudioContextFalso.criados).toHaveLength(1)
    expect(contexto!.resume).toHaveBeenCalled()
    expect(tonsTocados()).toHaveLength(2)
  })

  it('desligado, não toca e guarda a preferência', async () => {
    const { ligado, tocar } = criar()

    ligado.value = false
    await nextTick()
    tocar()

    expect(tonsTocados()).toHaveLength(0)
    expect(localStorage.getItem('pausa-ativa.som')).toBe('desligado')
  })

  it('lembra que o som foi desligado', () => {
    localStorage.setItem('pausa-ativa.som', 'desligado')

    expect(criar().ligado.value).toBe(false)
  })

  it('ligar o som toca uma amostra e guarda a preferência', async () => {
    localStorage.setItem('pausa-ativa.som', 'desligado')
    const { ligado } = criar()

    ligado.value = true
    await nextTick()

    expect(tonsTocados()).toHaveLength(1)
    expect(localStorage.getItem('pausa-ativa.som')).toBe('ligado')
  })

  it('depois de recarregar a página, fica bloqueado até o primeiro clique (D7)', () => {
    instalarNavegadorFalso({ usuarioJaInteragiu: false })
    const { bloqueado, tocar } = criar()
    expect(bloqueado.value).toBe(true)

    tocar()
    expect(AudioContextFalso.criados).toHaveLength(0)

    document.dispatchEvent(new Event('pointerdown'))
    expect(bloqueado.value).toBe(false)

    tocar()
    expect(tonsTocados()).toHaveLength(1)
  })

  it('uma tecla também libera o som', () => {
    instalarNavegadorFalso({ usuarioJaInteragiu: false })
    const { bloqueado } = criar()

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab' }))

    expect(bloqueado.value).toBe(false)
  })

  it('desligado, nunca aparece como bloqueado', () => {
    instalarNavegadorFalso({ usuarioJaInteragiu: false })
    localStorage.setItem('pausa-ativa.som', 'desligado')

    expect(criar().bloqueado.value).toBe(false)
  })

  it('sem como saber se houve interação, considera o som liberado', () => {
    Object.defineProperty(navigator, 'userActivation', { value: undefined, configurable: true })

    expect(criar().bloqueado.value).toBe(false)
  })

  it('sem Web Audio, não quebra', () => {
    vi.stubGlobal('AudioContext', undefined)

    expect(() => criar().tocar()).not.toThrow()
  })

  it('ao desmontar, fecha o contexto de áudio e para de esperar o clique', () => {
    instalarNavegadorFalso({ usuarioJaInteragiu: false })
    const remover = vi.spyOn(document, 'removeEventListener')
    const { tocar } = criar()
    document.dispatchEvent(new Event('pointerdown'))
    tocar()
    remover.mockClear()

    escopo.stop()

    expect(AudioContextFalso.criados[0]!.close).toHaveBeenCalled()
    expect(remover).toHaveBeenCalledWith('pointerdown', expect.any(Function), true)
    remover.mockRestore()
  })
})
