import { afterEach, describe, expect, it } from 'vitest'
import { effectScope } from 'vue'

import { abaDoEndereco, useAba } from '../useAba'

let escopo: ReturnType<typeof effectScope> | undefined
function criar() {
  escopo = effectScope()
  return escopo.run(useAba)!
}

function mudarOEndereco(hash: string) {
  window.location.hash = hash
  window.dispatchEvent(new HashChangeEvent('hashchange'))
}

describe('useAba', () => {
  afterEach(() => {
    escopo?.stop()
    escopo = undefined
    history.replaceState(null, '', '/')
  })

  it('#historico abre o Histórico; qualquer outro endereço, Hoje', () => {
    expect(abaDoEndereco('#historico')).toBe('historico')
    expect(abaDoEndereco('#hoje')).toBe('hoje')
    expect(abaDoEndereco('')).toBe('hoje')
    expect(abaDoEndereco('#outra')).toBe('hoje')
  })

  it('recarregar com #historico volta para o Histórico', () => {
    history.replaceState(null, '', '/#historico')

    expect(criar().aba.value).toBe('historico')
  })

  it('segue o endereço quando ele muda, como no Voltar do navegador', () => {
    const { aba } = criar()

    mudarOEndereco('#historico')
    expect(aba.value).toBe('historico')

    mudarOEndereco('#hoje')
    expect(aba.value).toBe('hoje')
  })

  it('ir para uma aba muda o endereço', () => {
    const { aba, ir } = criar()

    ir('historico')

    expect(aba.value).toBe('historico')
    expect(window.location.hash).toBe('#historico')
  })

  it('desfeito o escopo, para de ouvir o endereço', () => {
    const { aba } = criar()
    escopo?.stop()

    mudarOEndereco('#historico')

    expect(aba.value).toBe('hoje')
  })
})
