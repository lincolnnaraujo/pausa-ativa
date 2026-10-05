import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'

import { comMarco, umaJornada } from '@/__tests__/fabrica'
import { OperacaoRecusadaError, ServidorIndisponivelError } from '@/api/http'
import {
  buscarJornadaAtual,
  concluirMarco,
  iniciarJornada,
  type Jornada,
  pausarJornada,
} from '@/api/jornada'
import { lerUltimaDuracao, lerUltimaMeta } from '@/preferencias'

import { useJornada } from '../useJornada'

vi.mock('@/api/jornada', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/jornada')>()),
  buscarJornadaAtual: vi.fn(),
  iniciarJornada: vi.fn(),
  pausarJornada: vi.fn(),
  retomarJornada: vi.fn(),
  finalizarJornada: vi.fn(),
  concluirMarco: vi.fn(),
  falharMarco: vi.fn(),
}))

/** Cria o composable num escopo próprio, como um componente faria, e o desfaz no fim do teste. */
let escopo: ReturnType<typeof effectScope>
function criar() {
  escopo = effectScope()
  return escopo.run(useJornada)!
}

/** 1h30 trabalhada às 10:30, com os três primeiros marcos concluídos. */
function emAndamento(campos: Partial<Jornada> = {}): Jornada {
  let jornada = umaJornada({
    tempoTrabalhadoSegundos: 5_400,
    calculadoEm: '2026-10-02T10:30:00-03:00',
    aguaIngeridaMl: 562.5,
    ...campos,
  })
  for (const sequencia of [1, 2, 3]) {
    jornada = comMarco(jornada, sequencia, { status: 'CONCLUIDO' })
  }
  return jornada
}

describe('useJornada', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'performance'] })
    vi.mocked(buscarJornadaAtual).mockReset()
    vi.mocked(iniciarJornada).mockReset()
    vi.mocked(pausarJornada).mockReset()
    vi.mocked(concluirMarco).mockReset()
    localStorage.clear()
  })

  afterEach(() => {
    escopo.stop()
    vi.useRealTimers()
  })

  describe('cronômetro', () => {
    it('anda a partir do tempo calculado pelo servidor enquanto a jornada está em andamento', () => {
      const tela = criar()
      tela.aplicar(emAndamento())
      expect(tela.tempoTrabalhadoSegundos.value).toBe(5_400)

      vi.advanceTimersByTime(65_000)

      expect(tela.tempoTrabalhadoSegundos.value).toBe(5_465)
      expect(tela.segundosAteOProximo.value).toBe(7_200 - 5_465)
    })

    it('fica parado durante a pausa', () => {
      const tela = criar()
      tela.aplicar(emAndamento({ status: 'PAUSADA', pausadaDesde: '2026-10-02T10:30:00-03:00' }))

      vi.advanceTimersByTime(600_000)

      expect(tela.tempoTrabalhadoSegundos.value).toBe(5_400)
    })

    it('recomeça a contar a partir de cada nova situação recebida', () => {
      const tela = criar()
      tela.aplicar(emAndamento())
      vi.advanceTimersByTime(120_000)

      tela.aplicar(emAndamento({ tempoTrabalhadoSegundos: 5_520, calculadoEm: '2026-10-02T10:32:00-03:00' }))

      expect(tela.tempoTrabalhadoSegundos.value).toBe(5_520)
    })

    it('para de contar quando a tela é desmontada', () => {
      criar()
      expect(vi.getTimerCount()).toBe(1)

      escopo.stop()

      expect(vi.getTimerCount()).toBe(0)
    })
  })

  describe('situação da jornada', () => {
    it('ignora uma resposta mais velha que a situação já mostrada', () => {
      const tela = criar()
      const nova = emAndamento({ calculadoEm: '2026-10-02T10:31:00-03:00' })
      tela.aplicar(nova)

      tela.aplicar(emAndamento({ status: 'PAUSADA' }))

      expect(tela.jornada.value).toBe(nova)
    })

    it('aceita a situação de outra jornada mesmo com horário anterior', () => {
      const tela = criar()
      tela.aplicar(emAndamento())

      const outra = umaJornada({ id: 'jornada-2', calculadoEm: '2026-10-02T09:00:00-03:00' })
      tela.aplicar(outra)

      expect(tela.jornada.value).toBe(outra)
    })

    it('separa os pendentes e acha o próximo lembrete', () => {
      const tela = criar()
      tela.aplicar(comMarco(emAndamento(), 4, { status: 'PENDENTE' }))

      expect(tela.pendentes.value.map((marco) => marco.sequencia)).toEqual([4])
      expect(tela.proximo.value?.sequencia).toBe(5)
    })

    it('sem marco agendado, não há próximo lembrete', () => {
      const tela = criar()
      const jornada = emAndamento()
      tela.aplicar({ ...jornada, marcos: jornada.marcos.map((m) => ({ ...m, status: 'CONCLUIDO' })) })

      expect(tela.proximo.value).toBeNull()
      expect(tela.segundosAteOProximo.value).toBeNull()
    })

    it('reconhece o modo demonstração pelo intervalo do primeiro marco', () => {
      const tela = criar()
      expect(tela.modoDemonstracao.value).toBe(false)

      tela.aplicar(umaJornada())
      expect(tela.modoDemonstracao.value).toBe(false)

      tela.aplicar(umaJornada({ id: 'demo' }, 60))
      expect(tela.intervaloSegundos.value).toBe(60)
      expect(tela.modoDemonstracao.value).toBe(true)
    })
  })

  describe('carga', () => {
    it('carrega a jornada atual', async () => {
      vi.mocked(buscarJornadaAtual).mockResolvedValue(emAndamento())
      const tela = criar()
      expect(tela.carga.value).toBe('carregando')

      await tela.carregar()

      expect(tela.carga.value).toBe('pronta')
      expect(tela.jornada.value?.id).toBe('jornada-1')
    })

    it('marca como indisponível quando o servidor não responde', async () => {
      vi.mocked(buscarJornadaAtual).mockRejectedValue(new ServidorIndisponivelError('fora'))
      const tela = criar()

      await tela.carregar()

      expect(tela.carga.value).toBe('indisponivel')
      expect(tela.jornada.value).toBeNull()
    })
  })

  describe('comandos', () => {
    it('inicia o dia e guarda a meta e a duração do bloco para o dia seguinte', async () => {
      vi.mocked(iniciarJornada).mockResolvedValue(umaJornada({ metaAguaMl: 2_500 }))
      const tela = criar()

      const iniciou = await tela.iniciar(2_500, 10)

      expect(iniciou).toBe(true)
      expect(iniciarJornada).toHaveBeenCalledWith(2_500, 10)
      expect(tela.jornada.value?.metaAguaMl).toBe(2_500)
      expect(lerUltimaMeta()).toBe(2_500)
      expect(lerUltimaDuracao()).toBe(10)
    })

    it('meta recusada mostra o motivo, não recarrega nem guarda a meta', async () => {
      vi.mocked(iniciarJornada).mockRejectedValue(
        new OperacaoRecusadaError(400, 'A meta de água precisa ficar entre 1 e 6000 ml.'),
      )
      const tela = criar()

      const iniciou = await tela.iniciar(9_000, 10)

      expect(iniciou).toBe(false)
      expect(tela.erro.value).toBe('A meta de água precisa ficar entre 1 e 6000 ml.')
      expect(buscarJornadaAtual).not.toHaveBeenCalled()
      expect(lerUltimaMeta()).toBe(3_000)
      expect(lerUltimaDuracao()).toBe(5)
    })

    it('comando recusado por tela desatualizada mostra o motivo e busca a situação real', async () => {
      const vencido = comMarco(emAndamento({ calculadoEm: '2026-10-02T11:00:00-03:00' }), 4, {
        status: 'FALHA',
      })
      vi.mocked(concluirMarco).mockRejectedValue(
        new OperacaoRecusadaError(409, 'O marco já foi encerrado como FALHA.'),
      )
      vi.mocked(buscarJornadaAtual).mockResolvedValue(vencido)
      const tela = criar()
      tela.aplicar(comMarco(emAndamento(), 4, { status: 'PENDENTE' }))

      await tela.concluir('marco-4')

      expect(tela.erro.value).toBe('O marco já foi encerrado como FALHA.')
      expect(tela.jornada.value).toBe(vencido)
      expect(tela.ocupado.value).toBe(false)
    })

    it('com o servidor fora, mantém a tela e avisa', async () => {
      vi.mocked(pausarJornada).mockRejectedValue(new ServidorIndisponivelError('fora'))
      vi.mocked(buscarJornadaAtual).mockRejectedValue(new ServidorIndisponivelError('fora'))
      const tela = criar()
      const jornada = emAndamento()
      tela.aplicar(jornada)

      await tela.pausar()

      expect(tela.erro.value).toContain('Não foi possível falar com o servidor')
      expect(tela.jornada.value).toBe(jornada)
    })

    it('erro inesperado pede para recarregar a página', async () => {
      vi.mocked(pausarJornada).mockRejectedValue(new TypeError('quebrou'))
      const tela = criar()
      tela.aplicar(emAndamento())

      await tela.pausar()

      expect(tela.erro.value).toBe('Algo deu errado. Recarregue a página e tente de novo.')
    })

    it('ignora um segundo clique enquanto o primeiro comando não termina', async () => {
      let responder: (jornada: Jornada) => void = () => {}
      vi.mocked(pausarJornada).mockReturnValue(new Promise((resolve) => (responder = resolve)))
      const tela = criar()
      tela.aplicar(emAndamento())

      const primeiro = tela.pausar()
      expect(tela.ocupado.value).toBe(true)
      await expect(tela.pausar()).resolves.toBe(false)

      responder(emAndamento({ status: 'PAUSADA', calculadoEm: '2026-10-02T10:31:00-03:00' }))
      await primeiro
      await flushPromises()

      expect(pausarJornada).toHaveBeenCalledTimes(1)
      expect(tela.jornada.value?.status).toBe('PAUSADA')
      expect(tela.ocupado.value).toBe(false)
    })

    it('limpa a mensagem de erro no comando seguinte', async () => {
      vi.mocked(pausarJornada)
        .mockRejectedValueOnce(new ServidorIndisponivelError('fora'))
        .mockResolvedValueOnce(emAndamento({ status: 'PAUSADA' }))
      const tela = criar()
      tela.aplicar(emAndamento())

      await tela.pausar()
      await tela.pausar()

      expect(tela.erro.value).toBeNull()
    })

    it('sem jornada carregada, os comandos da jornada não fazem nada', async () => {
      const tela = criar()

      await expect(tela.pausar()).resolves.toBe(false)

      expect(pausarJornada).not.toHaveBeenCalled()
    })
  })
})
