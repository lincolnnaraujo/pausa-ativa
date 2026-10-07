import type { components } from './contrato'
import { requisitar } from './http'

/** Situação da jornada no instante `calculadoEm`. Horários em ISO-8601 no fuso de negócio. */
export type Jornada = components['schemas']['Jornada']
export type Marco = components['schemas']['Marco']
export type Bloco = components['schemas']['Bloco']
export type StatusJornada = Jornada['status']
export type StatusMarco = Marco['status']

/** Regras da meta de água (spec H2, seção 3.4; D4). */
export const META_PADRAO_ML = 3_000
export const META_MAXIMA_ML = 6_000

/** Durações do bloco de exercício que o dia aceita (spec H3, seção 3.4). */
export const DURACOES_DO_BLOCO_MIN = [5, 10] as const
export type DuracaoDoBlocoMin = (typeof DURACOES_DO_BLOCO_MIN)[number]
export const DURACAO_PADRAO_DO_BLOCO_MIN: DuracaoDoBlocoMin = 5

/** A jornada aberta ou a de hoje; `null` se ainda não houve jornada hoje (204). */
export async function buscarJornadaAtual(): Promise<Jornada | null> {
  const resposta = await requisitar('GET', '/api/v1/jornadas/atual')
  return resposta.status === 204 ? null : ((await resposta.json()) as Jornada)
}

/** A jornada de um dia, com os blocos propostos; `null` se não houve jornada no dia (204). */
export async function buscarJornadaDoDia(data: string): Promise<Jornada | null> {
  const resposta = await requisitar('GET', `/api/v1/jornadas?${new URLSearchParams({ data })}`)
  return resposta.status === 204 ? null : ((await resposta.json()) as Jornada)
}

/** Sem perfil físico, o backend recusa com 409 (spec H3, D7). */
export async function iniciarJornada(
  metaAguaMl: number,
  duracaoBlocoMin: DuracaoDoBlocoMin,
): Promise<Jornada> {
  const resposta = await requisitar('POST', '/api/v1/jornadas', {
    corpo: { metaAguaMl, duracaoBlocoMin },
  })
  return (await resposta.json()) as Jornada
}

export const pausarJornada = (id: string) => comando(`/api/v1/jornadas/${encodeURIComponent(id)}/pausa`)
export const retomarJornada = (id: string) =>
  comando(`/api/v1/jornadas/${encodeURIComponent(id)}/retomada`)
export const finalizarJornada = (id: string) =>
  comando(`/api/v1/jornadas/${encodeURIComponent(id)}/finalizacao`)

export const concluirMarco = (id: string) => comando(`/api/v1/marcos/${encodeURIComponent(id)}/conclusao`)
export const falharMarco = (id: string) => comando(`/api/v1/marcos/${encodeURIComponent(id)}/falha`)
/** Só o exercício pendente com `podeAdiar`; o bloco seguinte passa a ter 10 min (spec H3, seção 3.5). */
export const adiarMarco = (id: string) => comando(`/api/v1/marcos/${encodeURIComponent(id)}/adiamento`)

/** Avisa que o lembrete chegou à tela. Sem isso, o prazo vencido vira NAO_ENTREGUE em vez de FALHA. */
export async function confirmarRecebimento(id: string): Promise<void> {
  await requisitar('POST', `/api/v1/marcos/${encodeURIComponent(id)}/recebimento`)
}

async function comando(caminho: string): Promise<Jornada> {
  const resposta = await requisitar('POST', caminho)
  return (await resposta.json()) as Jornada
}
