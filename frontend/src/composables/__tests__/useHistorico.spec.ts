import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'

import { umaJornada, umaSemana, umPeriodoVazio } from '@/__tests__/fabrica'
import { buscarHistorico, type Historico } from '@/api/historico'
import { ServidorIndisponivelError } from '@/api/http'
import { buscarJornadaDoDia } from '@/api/jornada'

import { INTERVALO_ENTRE_ATUALIZACOES_MS, useHistorico } from '../useHistorico'

vi.mock('@/api/historico', () => ({ buscarHistorico: vi.fn() }))
vi.mock('@/api/jornada', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/jornada')>()),
  buscarJornadaDoDia: vi.fn(),
}))

let escopo: ReturnType<typeof effectScope>
function criar() {
  escopo = effectScope()
  return escopo.run(useHistorico)!
}

/** Promessa que o teste resolve quando quiser: uma resposta lenta do servidor. */
function adiada<T>() {
  let resolver!: (valor: T) => void
  const promessa = new Promise<T>((resolve) => (resolver = resolve))
  return { promessa, resolver }
}

describe('useHistorico', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // 10:00 de quarta, 7/10/2026, em São Paulo.
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    vi.setSystemTime(new Date('2026-10-07T13:00:00Z'))
    vi.mocked(buscarHistorico).mockResolvedValue(umaSemana())
    vi.mocked(buscarJornadaDoDia).mockResolvedValue(null)
  })

  afterEach(() => {
    escopo.stop()
    vi.useRealTimers()
  })

  it('abre na semana de hoje, sem buscar jornada', async () => {
    const historico = criar()

    expect(historico.carga.value).toBe('carregando')
    await historico.buscar()

    expect(buscarHistorico).toHaveBeenCalledWith('SEMANA', '2026-10-07')
    expect(buscarJornadaDoDia).not.toHaveBeenCalled()
    expect(historico.carga.value).toBe('pronta')
    expect(historico.historico.value).toEqual(umaSemana())
    expect(historico.noPeriodoDeHoje.value).toBe(true)
    expect(historico.podeAvancar.value).toBe(false)
  })

  it('anda para trás e para a frente, e o próximo para no período de hoje', async () => {
    const historico = criar()

    await historico.anterior()
    expect(buscarHistorico).toHaveBeenLastCalledWith('SEMANA', '2026-10-04')
    expect(historico.noPeriodoDeHoje.value).toBe(false)
    expect(historico.podeAvancar.value).toBe(true)

    await historico.proximo()
    expect(buscarHistorico).toHaveBeenLastCalledWith('SEMANA', '2026-10-05')
    expect(historico.podeAvancar.value).toBe(false)

    await historico.proximo()
    expect(buscarHistorico).toHaveBeenCalledTimes(2)
  })

  it('troca o tipo mantendo a data, abre um dia e volta para hoje', async () => {
    const historico = criar()
    await historico.anterior()

    await historico.escolherPeriodo('MES')
    expect(buscarHistorico).toHaveBeenLastCalledWith('MES', '2026-10-04')

    await historico.abrirDia('2026-09-29')
    expect(buscarHistorico).toHaveBeenLastCalledWith('DIA', '2026-09-29')
    expect(buscarJornadaDoDia).toHaveBeenCalledWith('2026-09-29')

    await historico.irParaHoje()
    expect(buscarHistorico).toHaveBeenLastCalledWith('DIA', '2026-10-07')
  })

  it('na visão do dia, guarda a jornada com os blocos', async () => {
    const jornada = umaJornada({ dataReferencia: '2026-10-05', status: 'FINALIZADA' })
    vi.mocked(buscarJornadaDoDia).mockResolvedValue(jornada)
    const historico = criar()

    await historico.abrirDia('2026-10-05')

    expect(historico.periodo.value).toBe('DIA')
    expect(historico.jornadaDoDia.value).toEqual(jornada)
  })

  it('só a resposta do último pedido aparece, mesmo que a anterior chegue depois', async () => {
    const lenta = adiada<Historico>()
    const vazia = umPeriodoVazio('SEMANA', ['2026-09-21'])
    vi.mocked(buscarHistorico).mockReturnValueOnce(lenta.promessa).mockResolvedValueOnce(vazia)
    const historico = criar()

    const primeira = historico.buscar()
    await historico.anterior()
    expect(historico.historico.value).toEqual(vazia)
    expect(historico.buscando.value).toBe(false)

    lenta.resolver(umaSemana())
    await primeira

    expect(historico.historico.value).toEqual(vazia)
  })

  it('enquanto busca, mantém o período anterior e marca que está buscando', async () => {
    const historico = criar()
    await historico.buscar()
    const lenta = adiada<Historico>()
    vi.mocked(buscarHistorico).mockReturnValueOnce(lenta.promessa)

    const busca = historico.anterior()

    expect(historico.buscando.value).toBe(true)
    expect(historico.historico.value).toEqual(umaSemana())
    lenta.resolver(umPeriodoVazio('SEMANA', ['2026-09-28']))
    await busca
    expect(historico.buscando.value).toBe(false)
  })

  it('com o servidor fora, fica indisponível com a explicação', async () => {
    vi.mocked(buscarHistorico).mockRejectedValue(new ServidorIndisponivelError('fora'))
    const historico = criar()

    await historico.buscar()

    expect(historico.carga.value).toBe('indisponivel')
    expect(historico.erro.value).toContain('Não foi possível falar com o servidor')
  })

  it('a jornada que muda faz buscar de novo, no máximo uma vez a cada 2 s', async () => {
    const historico = criar()
    await historico.buscar()

    historico.aoAtualizarJornada()
    historico.aoAtualizarJornada()
    vi.advanceTimersByTime(INTERVALO_ENTRE_ATUALIZACOES_MS - 1)
    expect(buscarHistorico).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(1)
    await flushPromises()
    expect(buscarHistorico).toHaveBeenCalledTimes(2)

    vi.advanceTimersByTime(INTERVALO_ENTRE_ATUALIZACOES_MS)
    historico.aoAtualizarJornada()
    vi.advanceTimersByTime(0)
    await flushPromises()
    expect(buscarHistorico).toHaveBeenCalledTimes(3)
  })

  it('fora do período de hoje, a jornada que muda não interessa', async () => {
    const historico = criar()
    await historico.anterior()

    historico.aoAtualizarJornada()
    vi.advanceTimersByTime(INTERVALO_ENTRE_ATUALIZACOES_MS)

    expect(buscarHistorico).toHaveBeenCalledTimes(1)
  })

  it('se a pessoa sai do período de hoje antes da espera, não busca', async () => {
    const historico = criar()
    await historico.buscar()

    historico.aoAtualizarJornada()
    await historico.anterior()
    vi.advanceTimersByTime(INTERVALO_ENTRE_ATUALIZACOES_MS)

    expect(buscarHistorico).toHaveBeenCalledTimes(2)
  })
})
