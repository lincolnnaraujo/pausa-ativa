import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { buscarStatus, ServidorIndisponivelError, type StatusDoSistema } from '@/api/sistema'

import HomeView from '../HomeView.vue'

vi.mock('@/api/sistema', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sistema')>()),
  buscarStatus: vi.fn(),
}))

const buscarStatusFalso = vi.mocked(buscarStatus)

/** 12:00 UTC é 09:00 em São Paulo. */
const NO_AR: StatusDoSistema = {
  aplicacao: 'pausa-ativa',
  versao: '0.1.0',
  agora: '2026-10-01T12:00:00Z',
  fuso: 'America/Sao_Paulo',
}

function cartao(wrapper: ReturnType<typeof mount>) {
  return wrapper.get('[data-testid="status-servidor"]')
}

describe('HomeView', () => {
  beforeEach(() => {
    buscarStatusFalso.mockReset()
  })

  it('mostra que está verificando enquanto o backend não responde', () => {
    buscarStatusFalso.mockReturnValue(new Promise(() => {}))

    const wrapper = mount(HomeView)

    expect(wrapper.get('h1').text()).toBe('Pausa Ativa')
    expect(cartao(wrapper).attributes('data-estado')).toBe('carregando')
    expect(cartao(wrapper).text()).toContain('Verificando o servidor…')
  })

  it('mostra a versão e o horário do servidor no fuso que ele informa', async () => {
    buscarStatusFalso.mockResolvedValue(NO_AR)

    const wrapper = mount(HomeView)
    await flushPromises()

    expect(cartao(wrapper).attributes('data-estado')).toBe('no-ar')
    expect(cartao(wrapper).text()).toContain('Servidor no ar')
    expect(wrapper.get('[data-testid="versao"]').text()).toBe('0.1.0')
    expect(wrapper.get('[data-testid="horario"]').text()).toBe('09:00')
  })

  it('mostra indisponível e volta ao ar ao tentar novamente', async () => {
    buscarStatusFalso
      .mockRejectedValueOnce(new ServidorIndisponivelError('O servidor não respondeu'))
      .mockResolvedValueOnce(NO_AR)

    const wrapper = mount(HomeView)
    await flushPromises()

    expect(cartao(wrapper).attributes('data-estado')).toBe('indisponivel')
    expect(cartao(wrapper).text()).toContain('Servidor indisponível')

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(buscarStatusFalso).toHaveBeenCalledTimes(2)
    expect(cartao(wrapper).attributes('data-estado')).toBe('no-ar')
  })
})
