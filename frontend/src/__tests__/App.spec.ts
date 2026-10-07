import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { comMarco, umaJornada, umaSemana } from '@/__tests__/fabrica'
import { EventSourceFalso, instalarNavegadorFalso } from '@/__tests__/navegador'
import { buscarHistorico } from '@/api/historico'
import { buscarJornadaAtual, confirmarRecebimento } from '@/api/jornada'
import { buscarPerfil } from '@/api/perfil'
import { buscarStatus } from '@/api/sistema'
import { INTERVALO_ENTRE_ATUALIZACOES_MS } from '@/composables/useHistorico'

import App from '../App.vue'

vi.mock('@/api/historico', () => ({ buscarHistorico: vi.fn() }))
vi.mock('@/api/jornada', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/jornada')>()),
  buscarJornadaAtual: vi.fn(),
  confirmarRecebimento: vi.fn(),
}))
vi.mock('@/api/perfil', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/perfil')>()),
  buscarPerfil: vi.fn(),
}))
vi.mock('@/api/sistema', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sistema')>()),
  buscarStatus: vi.fn(),
}))

enableAutoUnmount(afterEach)

/** 10:00 de quarta, 7/10, com o marco 2 de água pendente. */
const COM_PENDENTE = comMarco(
  umaJornada({
    dataReferencia: '2026-10-07',
    tempoTrabalhadoSegundos: 3_600,
    calculadoEm: '2026-10-07T10:00:00-03:00',
  }),
  2,
  { status: 'PENDENTE', disparadoEm: '2026-10-07T10:00:00-03:00', recebidoEm: '2026-10-07T10:00:01-03:00' },
)

/** Preso ao documento: o `isVisible` precisa dele para enxergar o `v-show`. */
async function montar() {
  const wrapper = mount(App, { attachTo: document.body })
  await flushPromises()
  return wrapper
}

function mudarOEndereco(hash: string) {
  window.location.hash = hash
  window.dispatchEvent(new HashChangeEvent('hashchange'))
}

function visivel(wrapper: VueWrapper, testid: string) {
  const elemento = wrapper.find(`[data-testid="${testid}"]`)
  return elemento.exists() && elemento.isVisible()
}

describe('App', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    instalarNavegadorFalso()
    vi.mocked(buscarStatus).mockReturnValue(new Promise(() => {}))
    vi.mocked(buscarJornadaAtual).mockResolvedValue(umaJornada({ dataReferencia: '2026-10-07' }))
    vi.mocked(confirmarRecebimento).mockResolvedValue()
    vi.mocked(buscarPerfil).mockResolvedValue({
      articulacoesPoupadas: [],
      nivel: 'INICIANTE',
      equipamentos: [],
      aceitaChao: true,
    })
    vi.mocked(buscarHistorico).mockResolvedValue(umaSemana())
    localStorage.clear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.useRealTimers()
    history.replaceState(null, '', '/')
  })

  it('abre em Hoje, com o cabeçalho e as duas abas, sem buscar o histórico', async () => {
    const wrapper = await montar()

    expect(wrapper.get('h1').text()).toBe('Pausa Ativa')
    expect(wrapper.get('[data-testid="aba-hoje"]').attributes('aria-current')).toBe('page')
    expect(wrapper.get('[data-testid="aba-historico"]').attributes('href')).toBe('#historico')
    expect(visivel(wrapper, 'hoje')).toBe(true)
    expect(wrapper.find('[data-testid="historico"]').exists()).toBe(false)
    expect(buscarHistorico).not.toHaveBeenCalled()
  })

  it('recarregar com #historico abre o Histórico, e a tela Hoje segue viva por baixo (D9)', async () => {
    history.replaceState(null, '', '/#historico')

    const wrapper = await montar()

    expect(wrapper.get('[data-testid="aba-historico"]').attributes('aria-current')).toBe('page')
    expect(visivel(wrapper, 'historico')).toBe(true)
    expect(visivel(wrapper, 'hoje')).toBe(false)
    expect(buscarJornadaAtual).toHaveBeenCalled()
    expect(EventSourceFalso.instancias).toHaveLength(1)
  })

  it('troca de aba pelo endereço e guarda o período escolhido no Histórico', async () => {
    const wrapper = await montar()

    mudarOEndereco('#historico')
    await flushPromises()
    expect(visivel(wrapper, 'historico')).toBe(true)
    await wrapper.get('button[aria-label="Período anterior"]').trigger('click')
    await flushPromises()

    mudarOEndereco('#hoje')
    await flushPromises()
    expect(visivel(wrapper, 'hoje')).toBe(true)
    expect(visivel(wrapper, 'historico')).toBe(false)

    mudarOEndereco('#historico')
    await flushPromises()
    expect(wrapper.get('[data-testid="periodo"]').text()).toBe('28 set – 4 out 2026')
  })

  it('o lembrete pendente aparece na aba Hoje e na faixa do Histórico, que leva de volta', async () => {
    vi.mocked(buscarJornadaAtual).mockResolvedValue(COM_PENDENTE)
    history.replaceState(null, '', '/#historico')
    const wrapper = await montar()

    expect(wrapper.get('[data-testid="aba-hoje"]').text()).toBe('Hoje · 1')
    expect(wrapper.get('[data-testid="faixa-pendente"]').text()).toContain('Um lembrete espera resposta')

    await wrapper.get('[data-testid="faixa-pendente"] button').trigger('click')

    expect(window.location.hash).toBe('#hoje')
    expect(visivel(wrapper, 'hoje')).toBe(true)
    expect(visivel(wrapper, 'marco-pendente')).toBe(true)
  })

  it('a jornada atualizada na tela Hoje faz o Histórico buscar o período de hoje de novo', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    vi.setSystemTime(new Date('2026-10-07T13:00:00Z'))
    history.replaceState(null, '', '/#historico')
    const wrapper = await montar()
    EventSourceFalso.ultima().abrir()
    await flushPromises()

    EventSourceFalso.ultima().emitir('jornada-atualizada', COM_PENDENTE)
    await flushPromises()
    vi.advanceTimersByTime(INTERVALO_ENTRE_ATUALIZACOES_MS)
    await flushPromises()

    expect(buscarHistorico).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="aba-hoje"]').text()).toBe('Hoje · 1')
  })
})
