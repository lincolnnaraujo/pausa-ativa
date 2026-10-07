import type { TipoDePeriodo } from '@/api/historico'
import { FUSO } from '@/formatacao'

/*
 * Datas do calendário em AAAA-MM-DD, como a API (spec H4, seção 3.2). As contas usam o Date em UTC só
 * como calendário: nenhuma depende do fuso do computador.
 */

const formatoDeHoje = new Intl.DateTimeFormat('en-CA', {
  timeZone: FUSO,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

/** O dia de hoje no fuso de negócio. */
export function dataDeHoje(agora: number = Date.now()): string {
  const partes = Object.fromEntries(formatoDeHoje.formatToParts(agora).map((parte) => [parte.type, parte.value]))
  return `${partes.year}-${partes.month}-${partes.day}`
}

function paraDate(data: string): Date {
  const [ano, mes, dia] = data.split('-').map(Number) as [number, number, number]
  return new Date(Date.UTC(ano, mes - 1, dia))
}

function paraTexto(data: Date): string {
  return data.toISOString().slice(0, 10)
}

function somarDias(data: string, dias: number): string {
  const resultado = paraDate(data)
  resultado.setUTCDate(resultado.getUTCDate() + dias)
  return paraTexto(resultado)
}

export interface Intervalo {
  inicio: string
  fim: string
}

/** Primeiro e último dia do período que contém a data. A semana vai de segunda a domingo (D2). */
export function intervaloDoPeriodo(tipo: TipoDePeriodo, data: string): Intervalo {
  switch (tipo) {
    case 'DIA':
      return { inicio: data, fim: data }
    case 'SEMANA': {
      const desdeSegunda = (paraDate(data).getUTCDay() + 6) % 7
      const inicio = somarDias(data, -desdeSegunda)
      return { inicio, fim: somarDias(inicio, 6) }
    }
    case 'MES': {
      const dia = paraDate(data)
      return {
        inicio: paraTexto(new Date(Date.UTC(dia.getUTCFullYear(), dia.getUTCMonth(), 1))),
        fim: paraTexto(new Date(Date.UTC(dia.getUTCFullYear(), dia.getUTCMonth() + 1, 0))),
      }
    }
  }
}

/** Uma data do período anterior (o dia antes do início) ou do seguinte (o dia depois do fim). */
export function periodoVizinho(tipo: TipoDePeriodo, data: string, sentido: -1 | 1): string {
  const { inicio, fim } = intervaloDoPeriodo(tipo, data)
  return sentido < 0 ? somarDias(inicio, -1) : somarDias(fim, 1)
}

export function contem(tipo: TipoDePeriodo, data: string, dia: string): boolean {
  const { inicio, fim } = intervaloDoPeriodo(tipo, data)
  return inicio <= dia && dia <= fim
}

const MESES = [
  'janeiro',
  'fevereiro',
  'março',
  'abril',
  'maio',
  'junho',
  'julho',
  'agosto',
  'setembro',
  'outubro',
  'novembro',
  'dezembro',
]
const MESES_CURTOS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']
const DIAS_DA_SEMANA_CURTOS = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb']

const formatoDoDia = new Intl.DateTimeFormat('pt-BR', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
})

/**
 * O período por extenso: "terça-feira, 6 de outubro de 2026", "28 set – 4 out 2026" ou "outubro de
 * 2026". Na semana, o mês e o ano só se repetem quando mudam.
 */
export function rotuloDoPeriodo(tipo: TipoDePeriodo, data: string): string {
  const { inicio, fim } = intervaloDoPeriodo(tipo, data)
  const de = paraDate(inicio)
  const ate = paraDate(fim)
  const diaMes = (dia: Date) => `${dia.getUTCDate()} ${MESES_CURTOS[dia.getUTCMonth()]}`
  switch (tipo) {
    case 'DIA':
      return formatoDoDia.format(de)
    case 'MES':
      return `${MESES[de.getUTCMonth()]} de ${de.getUTCFullYear()}`
    case 'SEMANA':
      if (de.getUTCFullYear() !== ate.getUTCFullYear()) {
        return `${diaMes(de)} ${de.getUTCFullYear()} – ${diaMes(ate)} ${ate.getUTCFullYear()}`
      }
      if (de.getUTCMonth() !== ate.getUTCMonth()) {
        return `${diaMes(de)} – ${diaMes(ate)} ${ate.getUTCFullYear()}`
      }
      return `${de.getUTCDate()} – ${diaMes(ate)} ${ate.getUTCFullYear()}`
  }
}

/** O dia da semana abreviado e o número do dia: `{ semana: 'seg', numero: 28 }`, para o eixo dos gráficos. */
export function partesDoDia(data: string): { semana: string; numero: number } {
  const dia = paraDate(data)
  return { semana: DIAS_DA_SEMANA_CURTOS[dia.getUTCDay()]!, numero: dia.getUTCDate() }
}

/** "seg 28/09": o dia numa linha de tabela. */
export function diaCurto(data: string): string {
  const dia = paraDate(data)
  const [, mes, numero] = data.split('-')
  return `${DIAS_DA_SEMANA_CURTOS[dia.getUTCDay()]} ${numero}/${mes}`
}

const NESTE_PERIODO: Record<TipoDePeriodo, string> = {
  DIA: 'neste dia',
  SEMANA: 'nesta semana',
  MES: 'neste mês',
}

/** "nesta semana", para as frases do estado vazio. */
export function nestePeriodo(tipo: TipoDePeriodo): string {
  return NESTE_PERIODO[tipo]
}
