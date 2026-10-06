import type { Bloco, Jornada, Marco } from '@/api/jornada'

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
    editadoEm: null,
    mensagem:
      sequencia % 2 === 1 ? 'Beba ~190 ml. Levante-se para buscar a água.' : 'Beba ~190 ml.',
    podeAdiar: false,
    podeCorrigir: false,
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

/** A mesma jornada com o marco de água `sequencia` alterado. */
export function comMarco(jornada: Jornada, sequencia: number, campos: Partial<Marco>): Jornada {
  return comMarcoDa(jornada, 'HIDRATACAO', sequencia, campos)
}

/** A mesma jornada com o marco de exercício `sequencia` alterado. */
export function comExercicio(jornada: Jornada, sequencia: number, campos: Partial<Marco>): Jornada {
  return comMarcoDa(jornada, 'EXERCICIO', sequencia, campos)
}

function comMarcoDa(
  jornada: Jornada,
  categoria: Marco['categoria'],
  sequencia: number,
  campos: Partial<Marco>,
): Jornada {
  return {
    ...jornada,
    marcos: jornada.marcos.map((marco) =>
      marco.categoria === categoria && marco.sequencia === sequencia ? { ...marco, ...campos } : marco,
    ),
  }
}

/** Bloco de 5 min como o backend devolve, com dois exercícios. */
export function umBloco(campos: Partial<Bloco> = {}): Bloco {
  return {
    duracaoMin: 5,
    segundosEstimados: 278,
    compensaAdiamento: false,
    itens: [
      {
        exercicio: 'Sentar e levantar da cadeira',
        grupo: 'Pernas',
        quantidade: '10 repetições',
        instrucao: 'Sente e levante da cadeira sem usar as mãos.',
      },
      {
        exercicio: 'Prancha',
        grupo: 'Core',
        quantidade: '20 s',
        instrucao: 'Antebraços no chão, corpo reto da cabeça aos pés.',
      },
    ],
    ...campos,
  }
}

/** Marco de exercício agendado: a cada 60 min, sem volume e sem bloco até disparar. */
export function umExercicio(sequencia: number, campos: Partial<Marco> = {}): Marco {
  return umMarco(sequencia, {
    id: `exercicio-${sequencia}`,
    categoria: 'EXERCICIO',
    volumeMl: null,
    volumeAproximadoMl: null,
    segundosTrabalhadosPrevistos: sequencia * 3_600,
    mensagem: 'Bloco de exercício.',
    ...campos,
  })
}

/** A jornada com os 8 exercícios, em ordem de horário como o backend devolve: na hora cheia, água antes. */
export function comExercicios(jornada: Jornada): Jornada {
  const exercicios = Array.from({ length: 8 }, (_, i) => umExercicio(i + 1))
  return {
    ...jornada,
    marcos: [...jornada.marcos, ...exercicios].sort(
      (a, b) =>
        a.segundosTrabalhadosPrevistos - b.segundosTrabalhadosPrevistos ||
        Number(a.categoria === 'EXERCICIO') - Number(b.categoria === 'EXERCICIO'),
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
