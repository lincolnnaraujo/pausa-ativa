import type { components } from './contrato'
import { requisitar } from './http'

/** Uma visão do histórico: o resumo de cada categoria e todos os dias do período (spec H4, seção 6). */
export type Historico = components['schemas']['Historico']
export type ResumoDaCategoria = components['schemas']['ResumoDaCategoria']
export type DiaDoHistorico = components['schemas']['DiaDoHistorico']
export type ContagemDoDia = components['schemas']['ContagemDoDia']
export type TipoDePeriodo = Historico['periodo']
export type Categoria = ResumoDaCategoria['categoria']

/** O período (dia, semana de segunda a domingo, ou mês) que contém a data. A data não pode ser futura. */
export async function buscarHistorico(periodo: TipoDePeriodo, data: string): Promise<Historico> {
  const parametros = new URLSearchParams({ periodo, data })
  const resposta = await requisitar('GET', `/api/v1/historico?${parametros}`)
  return (await resposta.json()) as Historico
}
