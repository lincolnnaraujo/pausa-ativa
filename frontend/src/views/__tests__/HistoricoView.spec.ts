import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  comExercicio,
  comExercicios,
  comMarco,
  umaContagem,
  umaJornada,
  umaSemana,
  umBloco,
  umDia,
  umPeriodoVazio,
  umResumo,
} from '@/__tests__/fabrica'
import { buscarHistorico, type Historico } from '@/api/historico'
import { ServidorIndisponivelError } from '@/api/http'
import { buscarJornadaDoDia, type Jornada } from '@/api/jornada'
import { INTERVALO_ENTRE_ATUALIZACOES_MS } from '@/composables/useHistorico'

import HistoricoView from '../HistoricoView.vue'

vi.mock('@/api/historico', () => ({ buscarHistorico: vi.fn() }))
vi.mock('@/api/jornada', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/jornada')>()),
  buscarJornadaDoDia: vi.fn(),
}))

enableAutoUnmount(afterEach)

/** Segunda, 5/10: finalizada às 17:40, com o primeiro bloco de exercício concluído. */
function segundaFeira(): Jornada {
  let jornada = comExercicios(
    umaJornada({
      id: 'jornada-5',
      dataReferencia: '2026-10-05',
      status: 'FINALIZADA',
      iniciadaEm: '2026-10-05T09:00:00-03:00',
      finalizadaEm: '2026-10-05T17:40:00-03:00',
      tempoTrabalhadoSegundos: 7.5 * 3_600,
      aguaIngeridaMl: 2_250,
    }),
  )
  jornada = comMarco(jornada, 1, { status: 'CONCLUIDO', disparadoEm: '2026-10-05T09:30:00-03:00' })
  return comExercicio(jornada, 1, {
    status: 'CONCLUIDO',
    disparadoEm: '2026-10-05T10:00:00-03:00',
    bloco: umBloco(),
  })
}

/** O dia 5/10 visto pelo histórico. */
function umDiaDoHistorico(): Historico {
  const semana = umaSemana()
  const segunda = semana.dias[0]!
  return {
    periodo: 'DIA',
    inicio: segunda.data,
    fim: segunda.data,
    categorias: segunda.categorias.map((contagem) => umResumo(contagem.categoria, { ...contagem })),
    dias: [segunda],
  }
}

async function montar(props: Partial<{ ativa: boolean; pendentes: number; versaoDaJornada: number }> = {}) {
  const wrapper = mount(HistoricoView, { props: { ativa: true, pendentes: 0, versaoDaJornada: 0, ...props } })
  await flushPromises()
  return wrapper
}

function botao(wrapper: VueWrapper, texto: string) {
  const encontrado = wrapper.findAll('button').find((b) => b.text() === texto)
  if (encontrado === undefined) {
    throw new Error(`Botão "${texto}" não está na tela`)
  }
  return encontrado
}

function porRotulo(wrapper: VueWrapper, rotulo: string) {
  return wrapper.get(`button[aria-label="${rotulo}"]`)
}

function texto(wrapper: VueWrapper, testid: string) {
  return wrapper.get(`[data-testid="${testid}"]`).text()
}

function existe(wrapper: VueWrapper, testid: string) {
  return wrapper.find(`[data-testid="${testid}"]`).exists()
}

describe('HistoricoView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // 10:00 de quarta, 7/10/2026, em São Paulo.
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    vi.setSystemTime(new Date('2026-10-07T13:00:00Z'))
    vi.mocked(buscarHistorico).mockResolvedValue(umaSemana())
    vi.mocked(buscarJornadaDoDia).mockResolvedValue(segundaFeira())
  })

  afterEach(() => vi.useRealTimers())

  it('mostra que está carregando até o servidor responder', () => {
    vi.mocked(buscarHistorico).mockReturnValue(new Promise(() => {}))

    const wrapper = mount(HistoricoView, { props: { ativa: true, pendentes: 0, versaoDaJornada: 0 } })

    expect(existe(wrapper, 'historico-carregando')).toBe(true)
  })

  describe('semana', () => {
    it('abre na semana de hoje, com a taxa, a meta e os dias na meta de cada categoria (Cenário 2)', async () => {
      const wrapper = await montar()

      expect(buscarHistorico).toHaveBeenCalledWith('SEMANA', '2026-10-07')
      expect(texto(wrapper, 'periodo')).toBe('5 – 11 out 2026')
      expect(wrapper.get('[aria-pressed="true"]').text()).toBe('Semana')
      const agua = wrapper.get('[data-testid="resumo-HIDRATACAO"]')
      expect(agua.get('h3').text()).toBe('💧 Água')
      expect(agua.get('[data-testid="taxa"]').text()).toBe('88,2%')
      expect(agua.get('[data-testid="meta"]').text()).toBe('Meta de 80%: atingida ✓')
      expect(agua.get('[data-testid="dias-na-meta"]').text()).toBe('Dias na meta: 2 de 3')
      expect(agua.findAll('dt').map((dt) => dt.text())).toEqual([
        'Concluídos',
        'Falhas',
        'Não entregues',
        'Em aberto',
      ])
      expect(agua.findAll('dd').map((dd) => dd.text())).toEqual(['30', '4', '2', '12'])
      const exercicio = wrapper.get('[data-testid="resumo-EXERCICIO"]')
      expect(exercicio.get('[data-testid="taxa"]').text()).toBe('75,0%')
      expect(exercicio.get('[data-testid="meta"]').text()).toBe('Meta de 80%: não atingida')
    })

    it('a tabela tem um dia por linha até hoje, com hoje em andamento', async () => {
      const wrapper = await montar()

      const linhas = wrapper.findAll('[data-testid="tabela-HIDRATACAO"] tbody tr')
      expect(linhas.map((linha) => linha.attributes('data-data'))).toEqual([
        '2026-10-05',
        '2026-10-06',
        '2026-10-07',
      ])
      expect(wrapper.findAll('[data-testid="tabela-HIDRATACAO"] thead th').map((th) => th.text())).toEqual([
        'Dia',
        'Taxa',
        'Concluído',
        'Falha',
        'Não entregue',
        'Não concluído',
      ])
      expect(linhas[0]!.findAll('th, td').map((celula) => celula.text())).toEqual([
        'seg 05/10',
        '85,7% ✓',
        '12',
        '2',
        '2',
        '0',
      ])
      expect(linhas[2]!.get('th').text()).toBe('qua 07/10 · em andamento')
      expect(wrapper.get('[data-testid="tabela-EXERCICIO"] tbody tr td').text()).toBe('71,4%')
    })

    it('no período de hoje, não avança nem oferece voltar para hoje', async () => {
      const wrapper = await montar()

      expect(porRotulo(wrapper, 'Próximo período').attributes('disabled')).toBeDefined()
      expect(wrapper.findAll('button').map((b) => b.text())).not.toContain('Voltar para hoje')
    })

    it('volta uma semana e depois volta para hoje', async () => {
      const wrapper = await montar()
      vi.mocked(buscarHistorico).mockResolvedValue(umPeriodoVazio('SEMANA', ['2026-09-28']))

      await porRotulo(wrapper, 'Período anterior').trigger('click')
      await flushPromises()

      expect(buscarHistorico).toHaveBeenLastCalledWith('SEMANA', '2026-10-04')
      expect(texto(wrapper, 'periodo')).toBe('28 set – 4 out 2026')
      expect(porRotulo(wrapper, 'Próximo período').attributes('disabled')).toBeUndefined()

      await botao(wrapper, 'Voltar para hoje').trigger('click')
      await flushPromises()

      expect(buscarHistorico).toHaveBeenLastCalledWith('SEMANA', '2026-10-07')
      expect(texto(wrapper, 'periodo')).toBe('5 – 11 out 2026')
    })

    it('depois de voltar, avança até o período de hoje', async () => {
      const wrapper = await montar()
      await porRotulo(wrapper, 'Período anterior').trigger('click')
      await flushPromises()

      await porRotulo(wrapper, 'Próximo período').trigger('click')
      await flushPromises()

      expect(buscarHistorico).toHaveBeenLastCalledWith('SEMANA', '2026-10-05')
      expect(texto(wrapper, 'periodo')).toBe('5 – 11 out 2026')
      expect(porRotulo(wrapper, 'Próximo período').attributes('disabled')).toBeDefined()
    })

    it('troca para o mês da mesma data', async () => {
      const wrapper = await montar()

      await botao(wrapper, 'Mês').trigger('click')
      await flushPromises()

      expect(buscarHistorico).toHaveBeenLastCalledWith('MES', '2026-10-07')
      expect(texto(wrapper, 'periodo')).toBe('outubro de 2026')
      expect(wrapper.get('[aria-pressed="true"]').text()).toBe('Mês')
    })

    it('enquanto busca o período novo, o anterior fica esmaecido, sem esqueleto', async () => {
      const wrapper = await montar()
      let responder!: (historico: Historico) => void
      vi.mocked(buscarHistorico).mockReturnValue(new Promise((resolve) => (responder = resolve)))

      await porRotulo(wrapper, 'Período anterior').trigger('click')

      const conteudo = wrapper.get('[data-testid="conteudo-do-historico"]')
      expect(conteudo.classes()).toContain('esmaecido')
      expect(conteudo.attributes('aria-busy')).toBe('true')
      expect(existe(wrapper, 'resumo-HIDRATACAO')).toBe(true)
      expect(texto(wrapper, 'periodo')).toBe('28 set – 4 out 2026')

      responder(umPeriodoVazio('SEMANA', ['2026-09-28']))
      await flushPromises()
      expect(wrapper.get('[data-testid="conteudo-do-historico"]').classes()).not.toContain('esmaecido')
    })
  })

  describe('períodos sem dados', () => {
    it('sem jornada no período, só a frase, sem taxa e sem zeros (Cenário 6)', async () => {
      vi.mocked(buscarHistorico).mockResolvedValue(
        umPeriodoVazio('SEMANA', ['2026-08-03', '2026-08-04', '2026-08-05']),
      )

      const wrapper = await montar()

      expect(texto(wrapper, 'historico-vazio')).toBe('Nenhuma jornada nesta semana.')
      expect(existe(wrapper, 'resumo-HIDRATACAO')).toBe(false)
      expect(wrapper.find('table').exists()).toBe(false)
    })

    it('categoria sem lembretes no período avisa no lugar da tabela, como nas jornadas da v0.2.0', async () => {
      const semana: Historico = {
        ...umaSemana(),
        categorias: [umResumo('HIDRATACAO', { concluidos: 14, falhas: 2, taxa: 87.5 }), umResumo('EXERCICIO')],
        dias: [
          umDia('2026-10-05', {
            jornada: 'FINALIZADA',
            categorias: [
              umaContagem('HIDRATACAO', { concluidos: 14, falhas: 2, taxa: 87.5, metaAtingida: true }),
              umaContagem('EXERCICIO'),
            ],
          }),
        ],
      }
      vi.mocked(buscarHistorico).mockResolvedValue(semana)

      const wrapper = await montar()

      expect(texto(wrapper, 'dias-EXERCICIO')).toBe('🏃 Exercício: sem dados nesta semana')
      expect(wrapper.get('[data-testid="resumo-EXERCICIO"] [data-testid="taxa"]').text()).toBe('sem dados')
      expect(wrapper.find('[data-testid="resumo-EXERCICIO"] [data-testid="meta"]').exists()).toBe(false)
      expect(existe(wrapper, 'tabela-HIDRATACAO')).toBe(true)
    })

    it('na tabela, um dia sem jornada diz isso, sem zeros', async () => {
      const semana = umaSemana()
      semana.dias[1] = umDia('2026-10-06')
      vi.mocked(buscarHistorico).mockResolvedValue(semana)

      const wrapper = await montar()

      const terca = wrapper.get('[data-testid="tabela-HIDRATACAO"] tr[data-data="2026-10-06"]')
      expect(terca.get('th').text()).toBe('ter 06/10')
      expect(terca.findAll('td').map((celula) => celula.text())).toEqual(['sem jornada'])
      expect(terca.find('button').exists()).toBe(false)
    })
  })

  describe('dia', () => {
    it('abre o dia pela tabela, com o resumo e a jornada (Cenário 1)', async () => {
      const wrapper = await montar()
      vi.mocked(buscarHistorico).mockResolvedValue(umDiaDoHistorico())

      await botao(wrapper, 'seg 05/10').trigger('click')
      await flushPromises()

      expect(buscarHistorico).toHaveBeenLastCalledWith('DIA', '2026-10-05')
      expect(buscarJornadaDoDia).toHaveBeenCalledWith('2026-10-05')
      expect(texto(wrapper, 'periodo')).toBe('segunda-feira, 5 de outubro de 2026')
      expect(wrapper.get('[aria-pressed="true"]').text()).toBe('Dia')
      const agua = wrapper.get('[data-testid="resumo-HIDRATACAO"]')
      expect(agua.get('[data-testid="taxa"]').text()).toBe('85,7%')
      expect(agua.findAll('dd').map((dd) => dd.text())).toEqual(['12', '2', '2'])
      expect(agua.find('[data-testid="dias-na-meta"]').exists()).toBe(false)
      expect(texto(wrapper, 'fim-do-dia')).toBe('17:40')
      expect(texto(wrapper, 'agua-do-dia')).toBe('2.250 ml de 3.000 ml')
      expect(wrapper.findAll('[data-testid="lista-de-marcos"] tbody tr')).toHaveLength(24)
    })

    it('cada bloco de exercício abre os exercícios propostos naquele dia', async () => {
      vi.mocked(buscarHistorico).mockResolvedValue(umDiaDoHistorico())
      const wrapper = await montar()
      await botao(wrapper, 'Dia').trigger('click')
      await flushPromises()

      const bloco = botao(wrapper, '🏃 Exercício 1')
      expect(bloco.attributes('aria-expanded')).toBe('false')
      expect(existe(wrapper, 'exercicios-do-marco')).toBe(false)

      await bloco.trigger('click')
      expect(bloco.attributes('aria-expanded')).toBe('true')
      expect(wrapper.findAll('[data-testid="exercicios-do-marco"] li').map((li) => li.text())).toEqual([
        'Sentar e levantar da cadeira: 10 repetições',
        'Prancha: 20 s',
      ])

      await bloco.trigger('click')
      expect(existe(wrapper, 'exercicios-do-marco')).toBe(false)
    })

    it('o bloco que compensou um adiamento diz isso', async () => {
      vi.mocked(buscarHistorico).mockResolvedValue(umDiaDoHistorico())
      vi.mocked(buscarJornadaDoDia).mockResolvedValue(
        comExercicio(segundaFeira(), 1, { bloco: umBloco({ duracaoMin: 10, compensaAdiamento: true }) }),
      )
      const wrapper = await montar()
      await botao(wrapper, 'Dia').trigger('click')
      await flushPromises()

      await botao(wrapper, '🏃 Exercício 1').trigger('click')

      expect(texto(wrapper, 'exercicios-do-marco')).toContain(
        'Bloco de 10 min, com o que foi adiado na hora anterior.',
      )
    })

    it('hoje em andamento aparece marcado (D11)', async () => {
      vi.mocked(buscarHistorico).mockResolvedValue({ ...umDiaDoHistorico(), inicio: '2026-10-07', fim: '2026-10-07' })
      vi.mocked(buscarJornadaDoDia).mockResolvedValue(
        umaJornada({ dataReferencia: '2026-10-07', status: 'PAUSADA', pausadaDesde: '2026-10-07T12:00:00-03:00' }),
      )
      const wrapper = await montar()

      await botao(wrapper, 'Dia').trigger('click')
      await flushPromises()

      expect(buscarHistorico).toHaveBeenLastCalledWith('DIA', '2026-10-07')
      expect(texto(wrapper, 'fim-do-dia')).toBe('em andamento')
    })

    it('a jornada encerrada automaticamente diz isso no fim', async () => {
      vi.mocked(buscarHistorico).mockResolvedValue(umDiaDoHistorico())
      vi.mocked(buscarJornadaDoDia).mockResolvedValue({
        ...segundaFeira(),
        status: 'ENCERRADA_AUTOMATICAMENTE',
        finalizadaEm: '2026-10-06T00:00:00-03:00',
      })
      const wrapper = await montar()

      await botao(wrapper, 'Dia').trigger('click')
      await flushPromises()

      expect(texto(wrapper, 'fim-do-dia')).toBe('00:00, encerrado automaticamente')
    })

    it('dia sem jornada mostra só a frase', async () => {
      vi.mocked(buscarHistorico).mockResolvedValue(umPeriodoVazio('DIA', ['2026-10-04']))
      vi.mocked(buscarJornadaDoDia).mockResolvedValue(null)
      const wrapper = await montar()

      await botao(wrapper, 'Dia').trigger('click')
      await flushPromises()

      expect(texto(wrapper, 'historico-vazio')).toBe('Nenhuma jornada neste dia.')
      expect(existe(wrapper, 'jornada-do-dia')).toBe(false)
    })
  })

  describe('com a tela Hoje', () => {
    it('avisa do lembrete pendente e leva de volta para Hoje', async () => {
      const wrapper = await montar({ pendentes: 1 })

      expect(texto(wrapper, 'faixa-pendente')).toContain('Um lembrete espera resposta na aba Hoje.')
      await botao(wrapper, 'Ir para Hoje').trigger('click')

      expect(wrapper.emitted('irParaHoje')).toHaveLength(1)
    })

    it('conta os lembretes quando são vários', async () => {
      const wrapper = await montar({ pendentes: 2 })

      expect(texto(wrapper, 'faixa-pendente')).toContain('2 lembretes esperam resposta na aba Hoje.')
    })

    it('a jornada que muda faz buscar de novo o período de hoje, no máximo a cada 2 s', async () => {
      const wrapper = await montar()

      await wrapper.setProps({ versaoDaJornada: 1 })
      await wrapper.setProps({ versaoDaJornada: 2 })
      vi.advanceTimersByTime(INTERVALO_ENTRE_ATUALIZACOES_MS)
      await flushPromises()

      expect(buscarHistorico).toHaveBeenCalledTimes(2)
    })

    it('escondido, não busca; ao voltar para a aba, busca de novo', async () => {
      const wrapper = await montar()

      await wrapper.setProps({ ativa: false })
      await wrapper.setProps({ versaoDaJornada: 1 })
      vi.advanceTimersByTime(INTERVALO_ENTRE_ATUALIZACOES_MS)
      expect(buscarHistorico).toHaveBeenCalledTimes(1)

      await wrapper.setProps({ ativa: true })
      await flushPromises()
      expect(buscarHistorico).toHaveBeenCalledTimes(2)
    })
  })

  it('com o servidor fora, explica e tenta de novo', async () => {
    vi.mocked(buscarHistorico)
      .mockRejectedValueOnce(new ServidorIndisponivelError('fora'))
      .mockResolvedValueOnce(umaSemana())
    const wrapper = await montar()

    expect(texto(wrapper, 'historico-indisponivel')).toContain('Não foi possível falar com o servidor')

    await botao(wrapper, 'Tentar novamente').trigger('click')
    await flushPromises()

    expect(existe(wrapper, 'resumo-HIDRATACAO')).toBe(true)
  })
})
