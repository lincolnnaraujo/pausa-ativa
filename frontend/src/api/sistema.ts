import type { components } from './contrato'

/** Resposta de GET /api/v1/sistema/status. `agora` vem em ISO-8601 com offset, no fuso de negócio. */
export type StatusDoSistema = components['schemas']['StatusResposta']

export const TEMPO_LIMITE_MS = 5_000

/** O backend não respondeu a tempo, a rede falhou ou a resposta não foi 200. */
export class ServidorIndisponivelError extends Error {
  override name = 'ServidorIndisponivelError'
}

export async function buscarStatus(tempoLimiteMs = TEMPO_LIMITE_MS): Promise<StatusDoSistema> {
  const controle = new AbortController()
  const temporizador = setTimeout(() => controle.abort(), tempoLimiteMs)

  try {
    const resposta = await fetch('/api/v1/sistema/status', {
      headers: { Accept: 'application/json' },
      signal: controle.signal,
    })
    if (!resposta.ok) {
      throw new ServidorIndisponivelError(`O servidor respondeu ${resposta.status}`)
    }
    return (await resposta.json()) as StatusDoSistema
  } catch (erro) {
    if (erro instanceof ServidorIndisponivelError) {
      throw erro
    }
    throw new ServidorIndisponivelError('O servidor não respondeu', { cause: erro })
  } finally {
    clearTimeout(temporizador)
  }
}
