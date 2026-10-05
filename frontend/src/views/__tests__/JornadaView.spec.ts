import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { comMarco, umaJornada, umMarco } from '@/__tests__/fabrica'
import { instalarNavegadorFalso } from '@/__tests__/navegador'
import { OperacaoRecusadaError, ServidorIndisponivelError } from '@/api/http'
import {
  buscarJornadaAtual,
  concluirMarco,
  confirmarRecebimento,
  falharMarco,
  finalizarJornada,
  iniciarJornada,
  type Jornada,
  pausarJornada,
  retomarJornada,
} from '@/api/jornada'
import { buscarPerfil, type Perfil, salvarPerfil } from '@/api/perfil'
import { buscarStatus } from '@/api/sistema'

import JornadaView from '../JornadaView.vue'

vi.mock('@/api/perfil', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/perfil')>()),
  buscarPerfil: vi.fn(),
  salvarPerfil: vi.fn(),
}))

const PERFIL: Perfil = {
  articulacoesPoupadas: [],
  nivel: 'INICIANTE',
  equipamentos: [],
  aceitaChao: true,
}

vi.mock('@/api/jornada', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/jornada')>()),
  buscarJornadaAtual: vi.fn(),
  iniciarJornada: vi.fn(),
  pausarJornada: vi.fn(),
  retomarJornada: vi.fn(),
  finalizarJornada: vi.fn(),
  concluirMarco: vi.fn(),
  falharMarco: vi.fn(),
  confirmarRecebimento: vi.fn(),
}))

vi.mock('@/api/sistema', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sistema')>()),
  buscarStatus: vi.fn(),
}))

enableAutoUnmount(afterEach)

/** 1h30 trabalhada às 10:30, três lembretes concluídos (Cenário 2). */
function emAndamento(campos: Partial<Jornada> = {}): Jornada {
  let jornada = umaJornada({
    tempoTrabalhadoSegundos: 5_400,
    calculadoEm: '2026-10-02T10:30:00-03:00',
    aguaIngeridaMl: 562.5,
    ...campos,
  })
  for (const [sequencia, disparadoEm] of [
    [1, '2026-10-02T09:30:01-03:00'],
    [2, '2026-10-02T10:00:01-03:00'],
    [3, '2026-10-02T10:30:00-03:00'],
  ] as const) {
    jornada = comMarco(jornada, sequencia, { status: 'CONCLUIDO', disparadoEm })
  }
  return jornada
}

async function montar(jornada: Jornada | null) {
  vi.mocked(buscarJornadaAtual).mockResolvedValue(jornada)
  const wrapper = mount(JornadaView)
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

function texto(wrapper: VueWrapper, testid: string) {
  return wrapper.get(`[data-testid="${testid}"]`).text()
}

function linhaDoMarco(wrapper: VueWrapper, sequencia: number) {
  return wrapper.findAll('[data-testid="lista-de-marcos"] tbody tr')[sequencia - 1]!
}

describe('JornadaView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    instalarNavegadorFalso()
    vi.mocked(buscarStatus).mockReturnValue(new Promise(() => {}))
    vi.mocked(confirmarRecebimento).mockResolvedValue()
    vi.mocked(buscarPerfil).mockResolvedValue(PERFIL)
    localStorage.clear()
  })

  afterEach(() => vi.unstubAllGlobals())

  describe('carga', () => {
    it('mostra que está carregando até o servidor responder', () => {
      vi.mocked(buscarJornadaAtual).mockReturnValue(new Promise(() => {}))

      const wrapper = mount(JornadaView)

      expect(wrapper.get('h1').text()).toBe('Pausa Ativa')
      expect(wrapper.find('[data-testid="carregando"]').exists()).toBe(true)
    })

    it('com o servidor fora, explica e tenta de novo', async () => {
      vi.mocked(buscarJornadaAtual)
        .mockRejectedValueOnce(new ServidorIndisponivelError('fora'))
        .mockResolvedValueOnce(null)
      const wrapper = mount(JornadaView)
      await flushPromises()

      expect(texto(wrapper, 'jornada-indisponivel')).toContain('Não foi possível carregar a jornada')

      await botao(wrapper, 'Tentar novamente').trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-testid="iniciar-dia"]').exists()).toBe(true)
    })
  })

  describe('sem jornada hoje', () => {
    it('sugere a meta padrão e inicia o dia com ela', async () => {
      vi.mocked(iniciarJornada).mockResolvedValue(umaJornada())
      const wrapper = await montar(null)

      expect(wrapper.get<HTMLInputElement>('#meta-agua').element.value).toBe('3000')

      await wrapper.get('form').trigger('submit')
      await flushPromises()

      expect(iniciarJornada).toHaveBeenCalledWith(3_000, 5)
      expect(wrapper.find('[data-testid="iniciar-dia"]').exists()).toBe(false)
      expect(texto(wrapper, 'situacao')).toBe('Em andamento desde 09:00')
      expect(texto(wrapper, 'proximo')).toBe('em 30 min')
    })

    it('escolhe blocos de 10 min e lembra a escolha no dia seguinte', async () => {
      vi.mocked(iniciarJornada).mockResolvedValue(umaJornada({ duracaoBlocoMin: 10 }))
      const wrapper = await montar(null)
      expect(wrapper.get<HTMLInputElement>('input[name="duracao-bloco"][value="5"]').element.checked).toBe(
        true,
      )

      await wrapper.get('input[name="duracao-bloco"][value="10"]').setValue(true)
      await wrapper.get('form').trigger('submit')
      await flushPromises()

      expect(iniciarJornada).toHaveBeenCalledWith(3_000, 10)
      expect(localStorage.getItem('pausa-ativa.duracao-bloco-min')).toBe('10')
    })

    it('mostra o resumo do perfil e permite editar antes de começar', async () => {
      const editado: Perfil = { ...PERFIL, articulacoesPoupadas: ['JOELHO'], aceitaChao: false }
      vi.mocked(salvarPerfil).mockResolvedValue(editado)
      const wrapper = await montar(null)
      expect(texto(wrapper, 'resumo-do-perfil')).toContain(
        'Iniciante · nada a poupar · sem equipamento · com exercícios no chão',
      )

      await botao(wrapper, 'Editar perfil').trigger('click')
      expect(wrapper.get('#titulo-perfil').text()).toBe('Editar perfil físico')
      await wrapper.get('input[name="articulacao"][value="JOELHO"]').setValue(true)
      await wrapper.get('input[name="chao"][value="false"]').setValue(true)
      await wrapper.get('[data-testid="formulario-do-perfil"]').trigger('submit')
      await flushPromises()

      expect(salvarPerfil).toHaveBeenCalledWith(editado)
      expect(texto(wrapper, 'resumo-do-perfil')).toContain('poupa joelho')
      expect(texto(wrapper, 'resumo-do-perfil')).toContain('sem exercícios no chão')
    })

    it('cancelar a edição volta ao início do dia sem gravar', async () => {
      const wrapper = await montar(null)

      await botao(wrapper, 'Editar perfil').trigger('click')
      await wrapper.get('input[name="nivel"][value="INTERMEDIARIO"]').setValue(true)
      await botao(wrapper, 'Cancelar').trigger('click')

      expect(salvarPerfil).not.toHaveBeenCalled()
      expect(texto(wrapper, 'resumo-do-perfil')).toContain('Iniciante')
    })

    it('sugere a última meta usada', async () => {
      localStorage.setItem('pausa-ativa.meta-agua-ml', '2500')

      const wrapper = await montar(null)

      expect(wrapper.get<HTMLInputElement>('#meta-agua').element.value).toBe('2500')
    })

    it.each(['0', '6001', '', '2500.5'])('recusa a meta "%s" sem chamar o servidor', async (meta) => {
      const wrapper = await montar(null)

      await wrapper.get('#meta-agua').setValue(meta)
      await wrapper.get('form').trigger('submit')

      expect(iniciarJornada).not.toHaveBeenCalled()
      expect(texto(wrapper, 'ajuda-meta')).toBe('Informe uma meta inteira entre 1 e 6.000 ml.')
      expect(wrapper.get('#meta-agua').attributes('aria-invalid')).toBe('true')
    })

    it('mostra o motivo quando o servidor recusa o início', async () => {
      vi.mocked(iniciarJornada).mockRejectedValue(
        new OperacaoRecusadaError(409, 'Já existe uma jornada hoje.'),
      )
      const wrapper = await montar(null)
      vi.mocked(buscarJornadaAtual).mockResolvedValue(emAndamento())

      await wrapper.get('form').trigger('submit')
      await flushPromises()

      expect(texto(wrapper, 'erro')).toBe('Já existe uma jornada hoje.')
      expect(wrapper.find('[data-testid="painel"]').exists()).toBe(true)
    })
  })

  describe('sem perfil (Cenário 6 da H3)', () => {
    beforeEach(() => vi.mocked(buscarPerfil).mockResolvedValue(null))

    it('pede o perfil antes do Iniciar dia, sem como pular', async () => {
      const wrapper = await montar(null)

      expect(wrapper.find('[data-testid="iniciar-dia"]').exists()).toBe(false)
      expect(wrapper.get('#titulo-perfil').text()).toBe('Seu perfil físico')
      expect(texto(wrapper, 'formulario-do-perfil')).toContain('Os blocos de exercício de cada hora')
      expect(wrapper.findAll('button').map((b) => b.text())).not.toContain('Cancelar')
    })

    it('com os padrões da spec, salva e libera o Iniciar dia', async () => {
      vi.mocked(salvarPerfil).mockResolvedValue(PERFIL)
      const wrapper = await montar(null)
      expect(wrapper.get<HTMLInputElement>('input[name="nivel"][value="INICIANTE"]').element.checked).toBe(
        true,
      )
      expect(wrapper.get<HTMLInputElement>('input[name="chao"][value="true"]').element.checked).toBe(true)

      await wrapper.get('[data-testid="formulario-do-perfil"]').trigger('submit')
      await flushPromises()

      expect(salvarPerfil).toHaveBeenCalledWith(PERFIL)
      expect(wrapper.find('[data-testid="iniciar-dia"]').exists()).toBe(true)
    })

    it('se o servidor recusar o perfil, mostra o motivo e mantém o formulário', async () => {
      vi.mocked(salvarPerfil).mockRejectedValue(new OperacaoRecusadaError(400, 'Nível inválido.'))
      const wrapper = await montar(null)

      await wrapper.get('[data-testid="formulario-do-perfil"]').trigger('submit')
      await flushPromises()

      expect(texto(wrapper, 'erro-perfil')).toBe('Nível inválido.')
      expect(wrapper.find('[data-testid="formulario-do-perfil"]').exists()).toBe(true)
    })

    it('se o backend recusar o início por falta de perfil, leva ao formulário', async () => {
      vi.mocked(buscarPerfil).mockResolvedValueOnce(PERFIL).mockResolvedValue(null)
      vi.mocked(iniciarJornada).mockRejectedValue(
        new OperacaoRecusadaError(409, 'Preencha o perfil físico antes de iniciar o dia.'),
      )
      const wrapper = await montar(null)

      await wrapper.get('form').trigger('submit')
      await flushPromises()

      expect(texto(wrapper, 'erro')).toBe('Preencha o perfil físico antes de iniciar o dia.')
      expect(wrapper.get('#titulo-perfil').text()).toBe('Seu perfil físico')
    })

    it('com o servidor fora na busca do perfil, explica e tenta de novo', async () => {
      vi.mocked(buscarPerfil)
        .mockRejectedValueOnce(new ServidorIndisponivelError('fora'))
        .mockResolvedValueOnce(PERFIL)
      const wrapper = await montar(null)
      expect(wrapper.find('[data-testid="jornada-indisponivel"]').exists()).toBe(true)

      await botao(wrapper, 'Tentar novamente').trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-testid="iniciar-dia"]').exists()).toBe(true)
    })
  })

  describe('em andamento', () => {
    it('mostra tempo trabalhado, água do dia e o próximo lembrete', async () => {
      const wrapper = await montar(emAndamento())

      expect(texto(wrapper, 'tempo-trabalhado')).toBe('01:30')
      expect(texto(wrapper, 'agua')).toBe('562,5 ml de 3.000 ml')
      expect(texto(wrapper, 'proximo')).toBe('em 30 min')
      expect(wrapper.find('[data-testid="modo-demonstracao"]').exists()).toBe(false)
    })

    it('mantém o cronômetro andando entre as atualizações', async () => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'performance'] })
      try {
        const wrapper = await montar(emAndamento())

        await vi.advanceTimersByTimeAsync(61_000)

        expect(texto(wrapper, 'tempo-trabalhado')).toBe('01:31')
        expect(texto(wrapper, 'proximo')).toBe('em 29 min')
      } finally {
        vi.useRealTimers()
      }
    })

    it('lista os lembretes com horário, volume e situação', async () => {
      const wrapper = await montar(emAndamento())

      expect(linhaDoMarco(wrapper, 1).text()).toBe('109:30~190 mlConcluído')
      // Próximo lembrete: previsto para 30 min depois do instante calculado pelo servidor.
      expect(linhaDoMarco(wrapper, 4).text()).toBe('4~11:00~190 mlAgendado')
      expect(linhaDoMarco(wrapper, 16).text()).toBe('16~17:00~190 mlAgendado')
    })

    it('permite editar o perfil durante o dia; vale para os próximos blocos', async () => {
      vi.mocked(salvarPerfil).mockResolvedValue({ ...PERFIL, nivel: 'INTERMEDIARIO' })
      const wrapper = await montar(emAndamento())

      await botao(wrapper, 'Editar perfil físico').trigger('click')
      expect(texto(wrapper, 'formulario-do-perfil')).toContain('As mudanças valem para os próximos blocos')
      await wrapper.get('input[name="nivel"][value="INTERMEDIARIO"]').setValue(true)
      await wrapper.get('[data-testid="formulario-do-perfil"]').trigger('submit')
      await flushPromises()

      expect(salvarPerfil).toHaveBeenCalledWith({ ...PERFIL, nivel: 'INTERMEDIARIO' })
      expect(wrapper.find('[data-testid="formulario-do-perfil"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="painel"]').exists()).toBe(true)
    })

    it('pausa o dia', async () => {
      vi.mocked(pausarJornada).mockResolvedValue(
        emAndamento({ status: 'PAUSADA', pausadaDesde: '2026-10-02T10:30:00-03:00' }),
      )
      const wrapper = await montar(emAndamento())

      await botao(wrapper, 'Pausar').trigger('click')
      await flushPromises()

      expect(pausarJornada).toHaveBeenCalledWith('jornada-1')
      expect(texto(wrapper, 'situacao')).toBe('Pausado desde 10:30')
    })

    it('mostra a faixa do modo demonstração quando o intervalo não é o padrão', async () => {
      const wrapper = await montar(umaJornada({}, 60))

      expect(texto(wrapper, 'modo-demonstracao')).toBe(
        'Modo demonstração: um lembrete a cada 1 min de tempo trabalhado.',
      )
    })
  })

  describe('pausada (Cenário 3)', () => {
    const PAUSADA = emAndamento({
      status: 'PAUSADA',
      pausadaDesde: '2026-10-02T12:00:00-03:00',
      tempoTrabalhadoSegundos: 3 * 3_600 + 20 * 60,
      calculadoEm: '2026-10-02T12:40:00-03:00',
    })

    it('congela o tempo, diz desde quando e não prevê horários', async () => {
      const wrapper = await montar(PAUSADA)

      expect(texto(wrapper, 'situacao')).toBe('Pausado desde 12:00')
      expect(texto(wrapper, 'tempo-trabalhado')).toBe('03:20')
      expect(texto(wrapper, 'proximo')).toBe('Parado durante a pausa')
      expect(linhaDoMarco(wrapper, 7).text()).toBe('7—~190 mlAgendado')
      expect(wrapper.findAll('button').map((b) => b.text())).not.toContain('Pausar')
    })

    it('retoma o dia', async () => {
      vi.mocked(retomarJornada).mockResolvedValue(
        emAndamento({ calculadoEm: '2026-10-02T13:00:00-03:00' }),
      )
      const wrapper = await montar(PAUSADA)

      await botao(wrapper, 'Retomar').trigger('click')
      await flushPromises()

      expect(retomarJornada).toHaveBeenCalledWith('jornada-1')
      expect(texto(wrapper, 'situacao')).toBe('Em andamento desde 09:00')
    })
  })

  describe('lembrete pendente', () => {
    const PENDENTE = comMarco(emAndamento({ tempoTrabalhadoSegundos: 7_200 }), 4, {
      status: 'PENDENTE',
      disparadoEm: '2026-10-02T11:00:00-03:00',
    })

    it('conta os lembretes de cada categoria em separado e marca a categoria no cartão', async () => {
      const exercicios = Array.from({ length: 8 }, (_, i) =>
        umMarco(i + 1, {
          id: `exercicio-${i + 1}`,
          categoria: 'EXERCICIO',
          volumeMl: null,
          volumeAproximadoMl: null,
          segundosTrabalhadosPrevistos: (i + 1) * 3_600,
          mensagem: 'Bloco de exercício.',
        }),
      )
      exercicios[0] = {
        ...exercicios[0]!,
        status: 'PENDENTE',
        disparadoEm: '2026-10-02T10:00:00-03:00',
        mensagem: 'Bloco de 5 min: 6 exercícios.',
      }
      const jornada = { ...PENDENTE, marcos: [...PENDENTE.marcos, ...exercicios] }

      const wrapper = await montar(jornada)

      const [agua, exercicio] = wrapper.findAll('[data-testid="marco-pendente"]')
      expect(agua!.text()).toContain('Lembrete 4 de 16')
      expect(agua!.attributes('data-categoria')).toBe('HIDRATACAO')
      expect(exercicio!.text()).toContain('Hora do exercício 🏃')
      expect(exercicio!.text()).toContain('Lembrete 1 de 8, às 10:00')
      expect(exercicio!.attributes('data-categoria')).toBe('EXERCICIO')
    })

    it('mostra o cartão com a mensagem e conclui (Cenário 2)', async () => {
      vi.mocked(concluirMarco).mockResolvedValue({
        ...comMarco(PENDENTE, 4, { status: 'CONCLUIDO' }),
        aguaIngeridaMl: 750,
        calculadoEm: '2026-10-02T11:01:00-03:00',
      })
      const wrapper = await montar(PENDENTE)

      const cartao = wrapper.get('[data-testid="marco-pendente"]')
      expect(cartao.text()).toContain('Hora da água 💧')
      expect(cartao.text()).toContain('Beba ~190 ml.')
      expect(cartao.text()).toContain('Lembrete 4 de 16, às 11:00')

      await botao(wrapper, 'Concluir').trigger('click')
      await flushPromises()

      expect(concluirMarco).toHaveBeenCalledWith('marco-4')
      expect(wrapper.find('[data-testid="marco-pendente"]').exists()).toBe(false)
      expect(texto(wrapper, 'agua')).toBe('750 ml de 3.000 ml')
      expect(linhaDoMarco(wrapper, 4).text()).toContain('Concluído')
    })

    it('marca falha', async () => {
      vi.mocked(falharMarco).mockResolvedValue(comMarco(PENDENTE, 4, { status: 'FALHA' }))
      const wrapper = await montar(PENDENTE)

      await botao(wrapper, 'Falhar').trigger('click')
      await flushPromises()

      expect(falharMarco).toHaveBeenCalledWith('marco-4')
      expect(linhaDoMarco(wrapper, 4).text()).toContain('Falha')
    })

    it('se o prazo venceu antes do clique, explica e mostra a situação real', async () => {
      vi.mocked(concluirMarco).mockRejectedValue(
        new OperacaoRecusadaError(409, 'O lembrete já foi encerrado como falha.'),
      )
      const wrapper = await montar(PENDENTE)
      vi.mocked(buscarJornadaAtual).mockResolvedValue({
        ...comMarco(PENDENTE, 4, { status: 'FALHA' }),
        calculadoEm: '2026-10-02T11:30:00-03:00',
      })

      await botao(wrapper, 'Concluir').trigger('click')
      await flushPromises()

      expect(texto(wrapper, 'erro')).toBe('O lembrete já foi encerrado como falha.')
      expect(wrapper.find('[data-testid="marco-pendente"]').exists()).toBe(false)
      expect(linhaDoMarco(wrapper, 4).text()).toContain('Falha')
    })
  })

  describe('finalização (Cenário 7)', () => {
    it('pede confirmação e permite desistir', async () => {
      const wrapper = await montar(emAndamento())

      await botao(wrapper, 'Finalizar dia').trigger('click')
      expect(texto(wrapper, 'confirmacao-finalizar')).toContain(
        'Finalizar? Os lembretes restantes não serão contados.',
      )

      await botao(wrapper, 'Cancelar').trigger('click')

      expect(wrapper.find('[data-testid="confirmacao-finalizar"]').exists()).toBe(false)
      expect(finalizarJornada).not.toHaveBeenCalled()
    })

    it('finaliza e mostra o resumo do dia', async () => {
      let finalizada = emAndamento({
        status: 'FINALIZADA',
        finalizadaEm: '2026-10-02T15:00:00-03:00',
        calculadoEm: '2026-10-02T15:00:00-03:00',
      })
      finalizada = comMarco(finalizada, 4, { status: 'FALHA' })
      finalizada = {
        ...finalizada,
        marcos: finalizada.marcos.map((m) => (m.status === 'AGENDADO' ? { ...m, status: 'NAO_CONCLUIDO' } : m)),
      }
      vi.mocked(finalizarJornada).mockResolvedValue(finalizada)
      const wrapper = await montar(emAndamento())

      await botao(wrapper, 'Finalizar dia').trigger('click')
      await botao(wrapper, 'Sim, finalizar').trigger('click')
      await flushPromises()

      expect(finalizarJornada).toHaveBeenCalledWith('jornada-1')
      const resumo = wrapper.get('[data-testid="resumo-do-dia"]')
      expect(resumo.get('h2').text()).toBe('Dia finalizado às 15:00')
      expect(texto(wrapper, 'agua-total')).toBe('562,5 ml de 3.000 ml')
      expect(texto(wrapper, 'contagem-CONCLUIDO')).toBe('3')
      expect(texto(wrapper, 'contagem-FALHA')).toBe('1')
      expect(texto(wrapper, 'contagem-NAO_CONCLUIDO')).toBe('12')
      expect(wrapper.find('[data-testid="contagem-NAO_ENTREGUE"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="painel"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="iniciar-dia"]').exists()).toBe(false)
    })

    it('distingue a jornada encerrada automaticamente', async () => {
      const wrapper = await montar(
        emAndamento({
          status: 'ENCERRADA_AUTOMATICAMENTE',
          finalizadaEm: '2026-10-02T23:59:00-03:00',
        }),
      )

      expect(wrapper.get('[data-testid="resumo-do-dia"] h2').text()).toBe(
        'Dia encerrado automaticamente às 23:59',
      )
    })
  })
})
