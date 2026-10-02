import { onScopeDispose, ref } from 'vue'

import type { Jornada, Marco } from '@/api/jornada'

export const CAMINHO_DOS_EVENTOS = '/api/v1/eventos'

/** Espera antes de abrir uma conexão nova quando o navegador desiste da anterior. Dobra a cada falha. */
export const ESPERA_INICIAL_MS = 2_000
export const ESPERA_MAXIMA_MS = 30_000

export type EstadoDaConexao = 'conectando' | 'conectada' | 'reconectando'

export interface OuvintesDeEventos {
  aoDispararMarco: (marco: Marco) => void
  aoAtualizarJornada: (jornada: Jornada) => void
  /** A conexão abriu. Em `reconexao`, eventos podem ter se perdido durante a queda. */
  aoConectar: (conexao: { reconexao: boolean }) => void
}

/**
 * Stream SSE do backend (spec H2, seção 7). O `EventSource` reconecta sozinho quando a conexão cai,
 * mas desiste de vez se a resposta não for um stream, como o 502 do nginx com o backend fora. Nesse
 * caso, uma conexão nova é aberta depois de uma espera crescente.
 */
export function useEventos(ouvintes: OuvintesDeEventos) {
  const estado = ref<EstadoDaConexao>('conectando')

  let fonte: EventSource | null = null
  let temporizador: ReturnType<typeof setTimeout> | undefined
  let espera = ESPERA_INICIAL_MS
  let houveQueda = false
  let encerrado = false

  function conectar() {
    const atual = new EventSource(CAMINHO_DOS_EVENTOS)
    fonte = atual

    atual.addEventListener('open', () => {
      estado.value = 'conectada'
      espera = ESPERA_INICIAL_MS
      ouvintes.aoConectar({ reconexao: houveQueda })
      houveQueda = false
    })

    atual.addEventListener('error', () => {
      estado.value = 'reconectando'
      houveQueda = true
      if (atual.readyState === EventSource.CLOSED && !encerrado) {
        temporizador = setTimeout(conectar, espera)
        espera = Math.min(espera * 2, ESPERA_MAXIMA_MS)
      }
    })

    ouvir<Marco>(atual, 'marco-disparado', ouvintes.aoDispararMarco)
    ouvir<Jornada>(atual, 'jornada-atualizada', ouvintes.aoAtualizarJornada)
  }

  conectar()

  onScopeDispose(() => {
    encerrado = true
    clearTimeout(temporizador)
    fonte?.close()
  })

  return { estado }
}

function ouvir<T>(fonte: EventSource, nome: string, ouvinte: (dados: T) => void) {
  fonte.addEventListener(nome, (evento) => {
    let dados: T
    try {
      dados = JSON.parse((evento as MessageEvent<string>).data) as T
    } catch {
      // Evento malformado: o próximo jornada-atualizada traz a situação completa.
      return
    }
    ouvinte(dados)
  })
}
