import { computed, onScopeDispose, ref, shallowRef } from 'vue'

import { buscarHistorico, type Historico, type TipoDePeriodo } from '@/api/historico'
import { buscarJornadaDoDia, type Jornada } from '@/api/jornada'
import { type EstadoDaCarga, mensagemDeErro } from '@/composables/useJornada'
import { contem, dataDeHoje, intervaloDoPeriodo, periodoVizinho } from '@/periodo'

/** Com o período de hoje à vista, a jornada que muda faz a tela buscar de novo, no máximo a cada 2 s. */
export const INTERVALO_ENTRE_ATUALIZACOES_MS = 2_000

/**
 * Estado da tela Histórico (spec H4, seção 8): o período escolhido, o resumo dele e, na visão do dia,
 * a jornada com os blocos. Abre na semana de hoje.
 */
export function useHistorico() {
  const hoje = ref(dataDeHoje())
  const periodo = ref<TipoDePeriodo>('SEMANA')
  const data = ref(hoje.value)
  const historico = shallowRef<Historico | null>(null)
  const jornadaDoDia = shallowRef<Jornada | null>(null)
  const carga = ref<EstadoDaCarga>('carregando')
  const buscando = ref(false)
  const erro = ref<string | null>(null)

  const noPeriodoDeHoje = computed(() => contem(periodo.value, data.value, hoje.value))
  /** O próximo período para no atual (spec H4, seção 3.2). */
  const podeAvancar = computed(() => intervaloDoPeriodo(periodo.value, data.value).fim < hoje.value)

  let ultimoPedido = 0
  let ultimaBuscaEm = Number.NEGATIVE_INFINITY
  let atualizacao: ReturnType<typeof setTimeout> | undefined
  onScopeDispose(() => clearTimeout(atualizacao))

  /**
   * Busca o período escolhido; enquanto isso, a tela mantém o anterior, esmaecido. Só a resposta do
   * último pedido vale: trocar de período depressa não põe um período velho por cima do novo.
   */
  async function buscar() {
    const pedido = ++ultimoPedido
    const tipo = periodo.value
    const dia = data.value
    hoje.value = dataDeHoje()
    ultimaBuscaEm = Date.now()
    buscando.value = true
    try {
      const [novo, jornada] = await Promise.all([
        buscarHistorico(tipo, dia),
        tipo === 'DIA' ? buscarJornadaDoDia(dia) : Promise.resolve(null),
      ])
      if (pedido === ultimoPedido) {
        historico.value = novo
        jornadaDoDia.value = jornada
        erro.value = null
        carga.value = 'pronta'
      }
    } catch (falha) {
      if (pedido === ultimoPedido) {
        erro.value = mensagemDeErro(falha)
        carga.value = 'indisponivel'
      }
    } finally {
      if (pedido === ultimoPedido) {
        buscando.value = false
      }
    }
  }

  function mostrar(tipo: TipoDePeriodo, dia: string) {
    periodo.value = tipo
    data.value = dia
    return buscar()
  }

  /**
   * A jornada mudou na tela Hoje (spec H4, seção 7). Só interessa se o período inclui hoje; uma rajada
   * de mudanças vira uma busca só, no máximo a cada 2 s.
   */
  function aoAtualizarJornada() {
    if (!noPeriodoDeHoje.value || atualizacao !== undefined) {
      return
    }
    const espera = Math.max(0, ultimaBuscaEm + INTERVALO_ENTRE_ATUALIZACOES_MS - Date.now())
    atualizacao = setTimeout(() => {
      atualizacao = undefined
      if (noPeriodoDeHoje.value) {
        void buscar()
      }
    }, espera)
  }

  return {
    hoje,
    periodo,
    data,
    historico,
    jornadaDoDia,
    carga,
    buscando,
    erro,
    noPeriodoDeHoje,
    podeAvancar,
    buscar,
    escolherPeriodo: (tipo: TipoDePeriodo) => mostrar(tipo, data.value),
    anterior: () => mostrar(periodo.value, periodoVizinho(periodo.value, data.value, -1)),
    proximo: () =>
      podeAvancar.value ? mostrar(periodo.value, periodoVizinho(periodo.value, data.value, 1)) : Promise.resolve(),
    irParaHoje: () => mostrar(periodo.value, dataDeHoje()),
    abrirDia: (dia: string) => mostrar('DIA', dia),
    aoAtualizarJornada,
  }
}
