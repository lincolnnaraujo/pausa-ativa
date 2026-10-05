import type { Jornada, Marco } from '@/api/jornada'

/** 09:00 em São Paulo, no dia dos testes. */
export const INICIO = '2026-10-02T09:00:00-03:00'

export function umMarco(sequencia: number, campos: Partial<Marco> = {}): Marco {
  return {
    id: `marco-${sequencia}`,
    categoria: 'HIDRATACAO',
    sequencia,
    status: 'AGENDADO',
    volumeMl: 187.5,
    volumeAproximadoMl: 190,
    segundosTrabalhadosPrevistos: sequencia * 1_800,
    disparadoEm: null,
    recebidoEm: null,
    respondidoEm: null,
    mensagem:
      sequencia % 2 === 1 ? 'Beba ~190 ml. Levante-se para buscar a água.' : 'Beba ~190 ml.',
    podeAdiar: false,
    bloco: null,
    ...campos,
  }
}

/** Jornada iniciada às 09:00 com os 16 marcos agendados, como o backend devolve ao iniciar. */
export function umaJornada(campos: Partial<Jornada> = {}, intervaloSegundos = 1_800): Jornada {
  return {
    id: 'jornada-1',
    dataReferencia: '2026-10-02',
    status: 'EM_ANDAMENTO',
    iniciadaEm: INICIO,
    finalizadaEm: null,
    pausadaDesde: null,
    tempoTrabalhadoSegundos: 0,
    calculadoEm: INICIO,
    metaAguaMl: 3_000,
    duracaoBlocoMin: 5,
    aguaIngeridaMl: 0,
    marcos: Array.from({ length: 16 }, (_, i) =>
      umMarco(i + 1, { segundosTrabalhadosPrevistos: (i + 1) * intervaloSegundos }),
    ),
    ...campos,
  }
}

/** A mesma jornada com o marco `sequencia` alterado. */
export function comMarco(jornada: Jornada, sequencia: number, campos: Partial<Marco>): Jornada {
  return {
    ...jornada,
    marcos: jornada.marcos.map((marco) =>
      marco.sequencia === sequencia ? { ...marco, ...campos } : marco,
    ),
  }
}

/** Resposta de erro do backend em Problem Details (RFC 9457). */
export function problema(status: number, detail: string): Response {
  return Response.json(
    { type: 'about:blank', title: 'Operação recusada', status, detail },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}
