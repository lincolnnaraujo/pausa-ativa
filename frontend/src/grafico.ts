import type { Categoria, ContagemDoDia, DiaDoHistorico, ResumoDaCategoria, TipoDePeriodo } from '@/api/historico'
import { taxa } from '@/formatacao'
import { diaCurto, partesDoDia } from '@/periodo'

/*
 * Geometria dos gráficos do histórico (spec H4, seção 8.1): colunas empilhadas por situação, uma por dia,
 * um gráfico por categoria. Funções puras, em pixels: o componente só desenha o que sai daqui.
 */

/** As situações empilhadas, de baixo para cima. Em aberto não entra: ainda não tem destino. */
export const SITUACOES = [
  { chave: 'concluidos', classe: 'concluido', rotulo: 'Concluído' },
  { chave: 'falhas', classe: 'falha', rotulo: 'Falha' },
  { chave: 'naoEntregues', classe: 'nao-entregue', rotulo: 'Não entregue' },
  { chave: 'naoConcluidos', classe: 'nao-concluido', rotulo: 'Não concluído' },
] as const satisfies readonly { chave: keyof ContagemDoDia; classe: string; rotulo: string }[]

export type Situacao = (typeof SITUACOES)[number]

/**
 * Cada categoria tem a sua escala, com marcas redondas: até 16 lembretes de água e 8 de exercício por
 * dia. A escala é fixa, e não a do maior dia do período: dois períodos se comparam pela altura.
 */
export const ESCALAS: Record<Categoria, { maximo: number; marcas: number[] }> = {
  HIDRATACAO: { maximo: 16, marcas: [0, 4, 8, 12, 16] },
  EXERCICIO: { maximo: 8, marcas: [0, 2, 4, 6, 8] },
}

export const ALTURA = 200
/** O topo guarda espaço para o ✓; a base, para as duas linhas do eixo da semana. */
export const MARGEM = { topo: 18, direita: 4, base: 34, esquerda: 28 } as const

const LARGURA_MAXIMA_DA_COLUNA = 24
const ESPACO_ENTRE_SEGMENTOS = 2
const RAIO_DO_TOPO = 4
/** Com faixas mais estreitas que isto, o eixo mostra só o dia 1 e os múltiplos de 5. */
const FAIXA_MINIMA_PARA_TODOS_OS_ROTULOS = 18

export interface Segmento {
  situacao: Situacao
  quantidade: number
  y: number
  altura: number
  /** O de cima ganha os cantos arredondados; a base fica reta. */
  topo: boolean
}

export interface Coluna {
  dia: DiaDoHistorico
  contagem: ContagemDoDia | undefined
  /** A faixa inteira do dia: a área de toque e de foco. */
  faixaX: number
  faixaLargura: number
  /** A coluna pintada, centrada na faixa. */
  x: number
  largura: number
  centro: number
  segmentos: Segmento[]
  /** Topo da pilha, onde vai o ✓ do dia que bateu a meta. */
  topoY: number
  /** Linhas do rótulo no eixo; vazio quando o dia não leva rótulo. */
  rotulo: string[]
}

export interface Geometria {
  largura: number
  base: number
  marcas: { valor: number; y: number }[]
  colunas: Coluna[]
}

const arredondar = (valor: number) => Math.round(valor * 100) / 100

export function geometria(
  dias: DiaDoHistorico[],
  categoria: Categoria,
  periodo: TipoDePeriodo,
  largura: number,
): Geometria {
  const alturaUtil = ALTURA - MARGEM.topo - MARGEM.base
  const base = MARGEM.topo + alturaUtil
  const faixa = (largura - MARGEM.esquerda - MARGEM.direita) / Math.max(dias.length, 1)
  const larguraDaColuna = Math.min(LARGURA_MAXIMA_DA_COLUNA, faixa * 0.6)
  const { maximo, marcas } = ESCALAS[categoria]
  const todosOsRotulos = faixa >= FAIXA_MINIMA_PARA_TODOS_OS_ROTULOS
  const paraPixels = (quantidade: number) => (quantidade / maximo) * alturaUtil

  const colunas = dias.map((dia, indice): Coluna => {
    const contagem = dia.categorias.find((candidata) => candidata.categoria === categoria)
    const faixaX = MARGEM.esquerda + indice * faixa
    const segmentos: Segmento[] = []
    let topoY = base
    for (const situacao of SITUACOES) {
      const quantidade = contagem?.[situacao.chave] ?? 0
      if (quantidade > 0) {
        const altura = paraPixels(quantidade)
        // Os 2 px de baixo ficam vazios, na cor do cartão, separando do segmento de baixo.
        const separacao = segmentos.length > 0 ? ESPACO_ENTRE_SEGMENTOS : 0
        topoY -= altura
        segmentos.push({
          situacao,
          quantidade,
          y: arredondar(topoY),
          altura: arredondar(Math.max(altura - separacao, 1)),
          topo: false,
        })
      }
    }
    const ultimo = segmentos.at(-1)
    if (ultimo !== undefined) {
      ultimo.topo = true
    }
    return {
      dia,
      contagem,
      faixaX: arredondar(faixaX),
      faixaLargura: arredondar(faixa),
      x: arredondar(faixaX + (faixa - larguraDaColuna) / 2),
      largura: arredondar(larguraDaColuna),
      centro: arredondar(faixaX + faixa / 2),
      segmentos,
      topoY: arredondar(topoY),
      rotulo: rotuloDoEixo(dia.data, periodo, todosOsRotulos),
    }
  })

  return {
    largura,
    base,
    marcas: marcas.map((valor) => ({ valor, y: arredondar(base - paraPixels(valor)) })),
    colunas,
  }
}

/** Na semana, "seg" e "5"; no mês, só o número, e só de cinco em cinco se a faixa for estreita. */
function rotuloDoEixo(data: string, periodo: TipoDePeriodo, todos: boolean): string[] {
  const { semana, numero } = partesDoDia(data)
  if (periodo === 'SEMANA') {
    return [semana, String(numero)]
  }
  return todos || numero === 1 || numero % 5 === 0 ? [String(numero)] : []
}

/** Retângulo com os cantos de cima arredondados e a base reta, como caminho SVG. */
export function topoArredondado(x: number, y: number, largura: number, altura: number): string {
  const r = arredondar(Math.min(RAIO_DO_TOPO, largura / 2, altura))
  const direita = arredondar(x + largura)
  const baixo = arredondar(y + altura)
  return (
    `M${x},${baixo}V${arredondar(y + r)}Q${x},${y} ${arredondar(x + r)},${y}` +
    `H${arredondar(direita - r)}Q${direita},${y} ${direita},${arredondar(y + r)}V${baixo}Z`
  )
}

function taxaEMeta(contagem: Pick<ContagemDoDia, 'taxa' | 'metaAtingida'>): string {
  if (contagem.taxa === null) {
    return 'sem dados'
  }
  return `taxa de ${taxa(contagem.taxa)}, meta ${contagem.metaAtingida ? 'atingida' : 'não atingida'}`
}

/**
 * O dia por extenso, para a dica de valores e para o leitor de tela: "seg 05/10: taxa de 85,7%, meta
 * atingida. Concluído 12, falha 2, não entregue 2, não concluído 0."
 */
export function descricaoDoDia(dia: DiaDoHistorico, contagem: ContagemDoDia): string {
  const emAndamento = dia.jornada === 'EM_ANDAMENTO' || dia.jornada === 'PAUSADA' ? ', em andamento' : ''
  const quantidades = SITUACOES.map(
    (situacao, indice) =>
      `${indice === 0 ? situacao.rotulo : situacao.rotulo.toLowerCase()} ${contagem[situacao.chave]}`,
  )
  if (contagem.emAberto > 0) {
    quantidades.push(`em aberto ${contagem.emAberto}`)
  }
  return `${diaCurto(dia.data)}${emAndamento}: ${taxaEMeta(contagem)}. ${quantidades.join(', ')}.`
}

const NOMES: Record<Categoria, string> = { HIDRATACAO: 'Água', EXERCICIO: 'Exercício' }

/** Título do gráfico para o leitor de tela: "Água, 28 set – 4 out 2026". */
export function tituloDoGrafico(categoria: Categoria, rotuloDoPeriodo: string): string {
  return `${NOMES[categoria]}, ${rotuloDoPeriodo}`
}

/** Descrição do gráfico: "52 concluídos, 9 falhas, 3 não entregues e 0 não concluídos; taxa de 85,2%." */
export function descricaoDoGrafico(resumo: ResumoDaCategoria): string {
  const situacoes =
    `${resumo.concluidos} concluídos, ${resumo.falhas} falhas, ${resumo.naoEntregues} não entregues` +
    ` e ${resumo.naoConcluidos} não concluídos`
  return `${situacoes}; ${resumo.taxa === null ? 'sem dados' : `taxa de ${taxa(resumo.taxa)}`}.`
}
