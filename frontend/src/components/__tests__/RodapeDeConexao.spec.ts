import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { buscarStatus, ServidorIndisponivelError, type StatusDoSistema } from '@/api/sistema'

import RodapeDeConexao from '../RodapeDeConexao.vue'

vi.mock('@/api/sistema', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sistema')>()),
  buscarStatus: vi.fn(),
}))

const buscarStatusFalso = vi.mocked(buscarStatus)

function status(versao: string): StatusDoSistema {
  return { aplicacao: 'pausa-ativa', versao, agora: '2026-10-02T12:00:00Z', fuso: 'America/Sao_Paulo' }
}

function rodape(wrapper: ReturnType<typeof mount>) {
  return wrapper.get('[data-testid="status-servidor"]')
}

describe('RodapeDeConexao', () => {
  beforeEach(() => {
    buscarStatusFalso.mockReset()
  })

  it('mostra que está conectando, sem buscar a versão ainda', () => {
    const wrapper = mount(RodapeDeConexao, { props: { conexao: 'conectando' } })

    expect(rodape(wrapper).attributes('data-estado')).toBe('conectando')
    expect(rodape(wrapper).text()).toBe('Conectando ao servidor…')
    expect(buscarStatusFalso).not.toHaveBeenCalled()
  })

  it('conectado, mostra a versão do backend', async () => {
    buscarStatusFalso.mockResolvedValue(status('0.2.0'))

    const wrapper = mount(RodapeDeConexao, { props: { conexao: 'conectada' } })
    await flushPromises()

    expect(rodape(wrapper).text()).toBe('Conectado ao servidor · versão 0.2.0')
  })

  it('conectado sem conseguir a versão, mostra só a conexão', async () => {
    buscarStatusFalso.mockRejectedValue(new ServidorIndisponivelError('fora'))

    const wrapper = mount(RodapeDeConexao, { props: { conexao: 'conectada' } })
    await flushPromises()

    expect(rodape(wrapper).text()).toBe('Conectado ao servidor')
  })

  it('na queda, avisa que está reconectando e, ao voltar, busca a versão de novo', async () => {
    buscarStatusFalso.mockResolvedValueOnce(status('0.2.0')).mockResolvedValueOnce(status('0.2.1'))
    const wrapper = mount(RodapeDeConexao, { props: { conexao: 'conectada' } })
    await flushPromises()

    await wrapper.setProps({ conexao: 'reconectando' })
    expect(rodape(wrapper).attributes('data-estado')).toBe('reconectando')
    expect(rodape(wrapper).text()).toBe('Sem conexão com o servidor. Reconectando…')

    await wrapper.setProps({ conexao: 'conectada' })
    await flushPromises()
    expect(wrapper.get('[data-testid="versao"]').text()).toBe('0.2.1')
  })
})
