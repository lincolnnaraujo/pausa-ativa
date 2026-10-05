import { computed, onScopeDispose, ref, shallowRef } from 'vue'

import { OperacaoRecusadaError, ServidorIndisponivelError } from '@/api/http'
import {
  buscarJornadaAtual,
  concluirMarco,
  type DuracaoDoBlocoMin,
  falharMarco,
  finalizarJornada,
  iniciarJornada,
  type Jornada,
  pausarJornada,
  retomarJornada,
} from '@/api/jornada'
import { guardarUltimaDuracao, guardarUltimaMeta } from '@/preferencias'

/** Intervalo padrão entre lembretes; outro valor indica o modo demonstração (spec H2, seção 9). */
export const INTERVALO_PADRAO_SEGUNDOS = 30 * 60

export type EstadoDaCarga = 'carregando' | 'pronta' | 'indisponivel'

/**
 * Estado da tela da jornada. O servidor calcula o tempo trabalhado; entre uma atualização e outra,
 * a tela soma o tempo decorrido medido por `performance.now()`, que não muda se alguém acertar o
 * relógio do computador.
 */
export function useJornada() {
  const jornada = shallowRef<Jornada | null>(null)
  const carga = ref<EstadoDaCarga>('carregando')
  const ocupado = ref(false)
  const erro = ref<string | null>(null)

  const recebidaEm = ref(performance.now())
  const agora = ref(recebidaEm.value)
  const relogio = setInterval(() => (agora.value = performance.now()), 1_000)
  onScopeDispose(() => clearInterval(relogio))

  /** Mostra a situação recebida, a menos que seja mais velha que a da tela (resposta atrasada). */
  function aplicar(nova: Jornada | null) {
    const atual = jornada.value
    if (
      nova !== null &&
      atual !== null &&
      nova.id === atual.id &&
      Date.parse(nova.calculadoEm) < Date.parse(atual.calculadoEm)
    ) {
      return
    }
    jornada.value = nova
    recebidaEm.value = performance.now()
    agora.value = recebidaEm.value
  }

  const tempoTrabalhadoSegundos = computed(() => {
    const atual = jornada.value
    if (atual === null) {
      return 0
    }
    if (atual.status !== 'EM_ANDAMENTO') {
      return atual.tempoTrabalhadoSegundos
    }
    const decorrido = Math.max(0, Math.floor((agora.value - recebidaEm.value) / 1_000))
    return atual.tempoTrabalhadoSegundos + decorrido
  })

  const marcos = computed(() => jornada.value?.marcos ?? [])
  const pendentes = computed(() => marcos.value.filter((marco) => marco.status === 'PENDENTE'))
  const proximo = computed(() => marcos.value.find((marco) => marco.status === 'AGENDADO') ?? null)
  const segundosAteOProximo = computed(() =>
    proximo.value === null
      ? null
      : proximo.value.segundosTrabalhadosPrevistos - tempoTrabalhadoSegundos.value,
  )

  /** O primeiro marco dispara depois de um intervalo: é dele que vem o intervalo da jornada. */
  const intervaloSegundos = computed(() => marcos.value[0]?.segundosTrabalhadosPrevistos ?? null)
  const modoDemonstracao = computed(
    () => intervaloSegundos.value !== null && intervaloSegundos.value !== INTERVALO_PADRAO_SEGUNDOS,
  )

  async function carregar() {
    carga.value = 'carregando'
    try {
      aplicar(await buscarJornadaAtual())
      carga.value = 'pronta'
    } catch {
      carga.value = 'indisponivel'
    }
  }

  /**
   * Recarrega sem trocar a tela por "carregando": depois de um comando recusado ou de uma reconexão.
   * Uma falha aqui não apaga o que já está à vista.
   */
  async function recarregar() {
    try {
      aplicar(await buscarJornadaAtual())
      carga.value = 'pronta'
    } catch {
      // Quem chamou já sinaliza o problema: a mensagem do comando ou o aviso de reconexão.
    }
  }

  async function executar(comando: () => Promise<Jornada>): Promise<boolean> {
    if (ocupado.value) {
      return false
    }
    ocupado.value = true
    erro.value = null
    try {
      aplicar(await comando())
      return true
    } catch (falha) {
      erro.value = mensagemDeErro(falha)
      // 404 e 409: a tela estava desatualizada (prazo venceu, outra aba respondeu). Busca a verdade.
      if (falha instanceof OperacaoRecusadaError && falha.status !== 400) {
        await recarregar()
      }
      return false
    } finally {
      ocupado.value = false
    }
  }

  /** @returns se o dia começou; a meta e a duração ficam guardadas para o próximo dia */
  async function iniciar(metaAguaMl: number, duracaoBlocoMin: DuracaoDoBlocoMin): Promise<boolean> {
    const iniciou = await executar(() => iniciarJornada(metaAguaMl, duracaoBlocoMin))
    if (iniciou) {
      guardarUltimaMeta(metaAguaMl)
      guardarUltimaDuracao(duracaoBlocoMin)
    }
    return iniciou
  }

  /** Comando sobre a jornada da tela; sem jornada carregada, não há o que fazer. */
  function naJornada(comando: (id: string) => Promise<Jornada>) {
    const atual = jornada.value
    return atual === null ? Promise.resolve(false) : executar(() => comando(atual.id))
  }

  return {
    jornada,
    carga,
    ocupado,
    erro,
    tempoTrabalhadoSegundos,
    pendentes,
    proximo,
    segundosAteOProximo,
    intervaloSegundos,
    modoDemonstracao,
    aplicar,
    carregar,
    recarregar,
    iniciar,
    pausar: () => naJornada(pausarJornada),
    retomar: () => naJornada(retomarJornada),
    finalizar: () => naJornada(finalizarJornada),
    concluir: (marcoId: string) => executar(() => concluirMarco(marcoId)),
    falhar: (marcoId: string) => executar(() => falharMarco(marcoId)),
  }
}

/** Texto para a tela: o motivo do backend, ou uma explicação quando o servidor não responde. */
export function mensagemDeErro(falha: unknown): string {
  if (falha instanceof OperacaoRecusadaError) {
    return falha.message
  }
  if (falha instanceof ServidorIndisponivelError) {
    return 'Não foi possível falar com o servidor. Confira se a aplicação está no ar e tente de novo.'
  }
  return 'Algo deu errado. Recarregue a página e tente de novo.'
}
