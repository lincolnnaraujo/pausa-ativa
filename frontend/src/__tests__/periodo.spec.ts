import { describe, expect, it } from 'vitest'

import {
  contem,
  dataDeHoje,
  diaCurto,
  intervaloDoPeriodo,
  nestePeriodo,
  periodoVizinho,
  rotuloDoPeriodo,
} from '@/periodo'

describe('períodos do histórico', () => {
  it('hoje é o dia em São Paulo, não o do computador nem o de UTC', () => {
    expect(dataDeHoje(Date.parse('2026-10-07T02:30:00Z'))).toBe('2026-10-06')
    expect(dataDeHoje(Date.parse('2026-10-07T03:00:00Z'))).toBe('2026-10-07')
  })

  it.each([
    ['DIA', '2026-10-07', '2026-10-07', '2026-10-07'],
    ['SEMANA', '2026-10-07', '2026-10-05', '2026-10-11'],
    ['SEMANA', '2026-10-05', '2026-10-05', '2026-10-11'],
    ['SEMANA', '2026-10-11', '2026-10-05', '2026-10-11'],
    ['SEMANA', '2026-01-01', '2025-12-29', '2026-01-04'],
    ['MES', '2026-10-07', '2026-10-01', '2026-10-31'],
    ['MES', '2028-02-10', '2028-02-01', '2028-02-29'],
  ] as const)('%s de %s vai de %s a %s, a semana de segunda a domingo', (tipo, data, inicio, fim) => {
    expect(intervaloDoPeriodo(tipo, data)).toEqual({ inicio, fim })
  })

  it.each([
    ['DIA', '2026-10-01', '2026-09-30', '2026-10-02'],
    ['SEMANA', '2026-10-07', '2026-10-04', '2026-10-12'],
    ['MES', '2026-03-31', '2026-02-28', '2026-04-01'],
    ['MES', '2026-01-15', '2025-12-31', '2026-02-01'],
  ] as const)('%s vizinho de %s: o anterior em %s, o seguinte em %s', (tipo, data, anterior, seguinte) => {
    expect(periodoVizinho(tipo, data, -1)).toBe(anterior)
    expect(periodoVizinho(tipo, data, 1)).toBe(seguinte)
  })

  it('sabe se um dia está no período', () => {
    expect(contem('SEMANA', '2026-10-05', '2026-10-11')).toBe(true)
    expect(contem('SEMANA', '2026-10-05', '2026-10-12')).toBe(false)
    expect(contem('DIA', '2026-10-05', '2026-10-05')).toBe(true)
  })

  it.each([
    ['DIA', '2026-10-06', 'terça-feira, 6 de outubro de 2026'],
    ['SEMANA', '2026-10-01', '28 set – 4 out 2026'],
    ['SEMANA', '2026-10-07', '5 – 11 out 2026'],
    ['SEMANA', '2026-01-01', '29 dez 2025 – 4 jan 2026'],
    ['MES', '2026-10-07', 'outubro de 2026'],
    ['MES', '2026-03-01', 'março de 2026'],
  ] as const)('%s de %s por extenso: "%s"', (tipo, data, rotulo) => {
    expect(rotuloDoPeriodo(tipo, data)).toBe(rotulo)
  })

  it('o dia numa linha de tabela e o período nas frases', () => {
    expect(diaCurto('2026-09-28')).toBe('seg 28/09')
    expect(diaCurto('2026-10-10')).toBe('sáb 10/10')
    expect(nestePeriodo('DIA')).toBe('neste dia')
    expect(nestePeriodo('SEMANA')).toBe('nesta semana')
    expect(nestePeriodo('MES')).toBe('neste mês')
  })
})
