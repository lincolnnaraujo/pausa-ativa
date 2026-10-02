import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { buscarStatus, ServidorIndisponivelError, type StatusDoSistema } from '@/api/sistema'

import RodapeDeConexao from '../RodapeDeConexao.vue'

vi.mock('@/api/sistema', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sistema')>()),
  buscarStatus: vi.fn(),
}))

const buscarStatusFalso = vi.mocked(buscarStatus)

const NO_AR: StatusDoSistema = {
  aplicacao: 'pausa-ativa',
  versao: '0.2.0',
  agora: '2026-10-02T12:00:00Z',
  fuso: 'America/Sao_Paulo',
}

function rodape(wrapper: ReturnType<typeof mount>) {
  return wrapper.get('[data-testid="status-servidor"]')
}

describe('RodapeDeConexao', () => {
  beforeEach(() => {
    buscarStatusFalso.mockReset()
  })

  it('mostra que está verificando enquanto o backend não responde', () => {
    buscarStatusFalso.mockReturnValue(new Promise(() => {}))

    const wrapper = mount(RodapeDeConexao)

    expect(rodape(wrapper).attributes('data-estado')).toBe('carregando')
    expect(rodape(wrapper).text()).toContain('Verificando o servidor…')
  })

  it('mostra que o servidor está no ar, com a versão', async () => {
    buscarStatusFalso.mockResolvedValue(NO_AR)

    const wrapper = mount(RodapeDeConexao)
    await flushPromises()

    expect(rodape(wrapper).attributes('data-estado')).toBe('no-ar')
    expect(rodape(wrapper).text()).toContain('Servidor no ar')
    expect(wrapper.get('[data-testid="versao"]').text()).toBe('0.2.0')
  })

  it('mostra indisponível e volta ao ar ao tentar novamente', async () => {
    buscarStatusFalso
      .mockRejectedValueOnce(new ServidorIndisponivelError('O servidor não respondeu'))
      .mockResolvedValueOnce(NO_AR)

    const wrapper = mount(RodapeDeConexao)
    await flushPromises()

    expect(rodape(wrapper).attributes('data-estado')).toBe('indisponivel')
    expect(rodape(wrapper).text()).toContain('Servidor indisponível')

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(buscarStatusFalso).toHaveBeenCalledTimes(2)
    expect(rodape(wrapper).attributes('data-estado')).toBe('no-ar')
  })
})
