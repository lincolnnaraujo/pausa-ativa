import type {
  Categoria,
  ContagemDoDia,
  DiaDoHistorico,
  Historico,
  ResumoDaCategoria,
} from '@/api/historico'
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

/** Uma categoria num dia, zerada e sem taxa, com os campos dados. */
export function umaContagem(categoria: Categoria, campos: Partial<ContagemDoDia> = {}): ContagemDoDia {
  return {
    categoria,
    concluidos: 0,
    falhas: 0,
    naoEntregues: 0,
    naoConcluidos: 0,
    emAberto: 0,
    taxa: null,
    metaAtingida: null,
    ...campos,
  }
}

/** Uma categoria no período, zerada e sem taxa, com os campos dados. */
export function umResumo(categoria: Categoria, campos: Partial<ResumoDaCategoria> = {}): ResumoDaCategoria {
  return { ...umaContagem(categoria), diasComDados: 0, diasNaMeta: 0, ...campos }
}

/** Um dia do período, sem jornada, com os campos dados. */
export function umDia(data: string, campos: Partial<DiaDoHistorico> = {}): DiaDoHistorico {
  return { data, jornada: null, futuro: false, categorias: [], ...campos }
}

/**
 * A semana de 5 a 11/10/2026, com hoje na quarta, 7/10, em andamento. Segunda bateu a meta na água e
 * não no exercício; terça também; quinta a domingo ainda não chegaram.
 */
export function umaSemana(): Historico {
  return {
    periodo: 'SEMANA',
    inicio: '2026-10-05',
    fim: '2026-10-11',
    categorias: [
      umResumo('HIDRATACAO', {
        concluidos: 30,
        falhas: 4,
        naoEntregues: 2,
        emAberto: 12,
        taxa: 88.2,
        metaAtingida: true,
        diasComDados: 3,
        diasNaMeta: 2,
      }),
      umResumo('EXERCICIO', {
        concluidos: 12,
        falhas: 4,
        naoEntregues: 1,
        emAberto: 7,
        taxa: 75,
        metaAtingida: false,
        diasComDados: 3,
        diasNaMeta: 1,
      }),
    ],
    dias: [
      umDia('2026-10-05', {
        jornada: 'FINALIZADA',
        categorias: [
          umaContagem('HIDRATACAO', { concluidos: 12, falhas: 2, naoEntregues: 2, taxa: 85.7, metaAtingida: true }),
          umaContagem('EXERCICIO', { concluidos: 5, falhas: 2, naoEntregues: 1, taxa: 71.4, metaAtingida: false }),
        ],
      }),
      umDia('2026-10-06', {
        jornada: 'FINALIZADA',
        categorias: [
          umaContagem('HIDRATACAO', { concluidos: 15, falhas: 1, taxa: 93.7, metaAtingida: true }),
          umaContagem('EXERCICIO', { concluidos: 6, falhas: 2, taxa: 75, metaAtingida: false }),
        ],
      }),
      umDia('2026-10-07', {
        jornada: 'EM_ANDAMENTO',
        categorias: [
          umaContagem('HIDRATACAO', { concluidos: 3, falhas: 1, emAberto: 12, taxa: 75, metaAtingida: false }),
          umaContagem('EXERCICIO', { concluidos: 1, emAberto: 7, taxa: 100, metaAtingida: true }),
        ],
      }),
      ...['2026-10-08', '2026-10-09', '2026-10-10', '2026-10-11'].map((data) => umDia(data, { futuro: true })),
    ],
  }
}

/** Um período sem nenhuma jornada (Cenário 6). */
export function umPeriodoVazio(periodo: Historico['periodo'], datas: string[]): Historico {
  return {
    periodo,
    inicio: datas[0]!,
    fim: datas.at(-1)!,
    categorias: [umResumo('HIDRATACAO'), umResumo('EXERCICIO')],
    dias: datas.map((data) => umDia(data)),
  }
}

/** Resposta de erro do backend em Problem Details (RFC 9457). */
export function problema(status: number, detail: string): Response {
  return Response.json(
    { type: 'about:blank', title: 'Operação recusada', status, detail },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}
