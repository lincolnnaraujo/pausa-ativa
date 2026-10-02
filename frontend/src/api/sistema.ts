import type { components } from './contrato'
import { requisitar, TEMPO_LIMITE_MS } from './http'

export { ServidorIndisponivelError, TEMPO_LIMITE_MS } from './http'

/** Resposta de GET /api/v1/sistema/status. `agora` vem em ISO-8601 com offset, no fuso de negócio. */
export type StatusDoSistema = components['schemas']['StatusResposta']

export async function buscarStatus(tempoLimiteMs = TEMPO_LIMITE_MS): Promise<StatusDoSistema> {
  const resposta = await requisitar('GET', '/api/v1/sistema/status', { tempoLimiteMs })
  return (await resposta.json()) as StatusDoSistema
}
