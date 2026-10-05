import { describe, expect, it } from 'vitest'

import {
  duracao,
  horario,
  intervalo,
  mililitros,
  resumoDoPerfil,
  rotuloDoStatus,
  tempoAte,
  tituloDoMarco,
} from '@/formatacao'

describe('formatação', () => {
  it('mostra o horário no fuso de São Paulo, qualquer que seja o offset recebido', () => {
    expect(horario('2026-10-02T12:00:00Z')).toBe('09:00')
    expect(horario('2026-10-02T09:30:01.123-03:00')).toBe('09:30')
    expect(horario(Date.parse('2026-10-02T16:45:00Z'))).toBe('13:45')
  })

  it('mostra o tempo trabalhado em HH:mm, sem segundos', () => {
    expect(duracao(0)).toBe('00:00')
    expect(duracao(59)).toBe('00:00')
    expect(duracao(5_400)).toBe('01:30')
    expect(duracao(8.5 * 3_600)).toBe('08:30')
    expect(duracao(-5)).toBe('00:00')
  })

  it('mostra volumes em ml no formato brasileiro, com até uma casa', () => {
    expect(mililitros(562.5)).toBe('562,5 ml')
    expect(mililitros(3_000)).toBe('3.000 ml')
    expect(mililitros(156.25)).toBe('156,3 ml')
    expect(mililitros(0)).toBe('0 ml')
  })

  it('arredonda o tempo até o próximo lembrete para cima', () => {
    expect(tempoAte(720)).toBe('em 12 min')
    expect(tempoAte(690)).toBe('em 12 min')
    expect(tempoAte(30)).toBe('em 1 min')
    expect(tempoAte(0)).toBe('agora')
  })

  it('descreve o intervalo entre lembretes', () => {
    expect(intervalo(1_800)).toBe('30 min')
    expect(intervalo(60)).toBe('1 min')
    expect(intervalo(45)).toBe('45 s')
  })

  it('traduz o status do marco', () => {
    expect(rotuloDoStatus('CONCLUIDO')).toBe('Concluído')
    expect(rotuloDoStatus('NAO_ENTREGUE')).toBe('Não entregue')
    expect(rotuloDoStatus('NAO_CONCLUIDO')).toBe('Não concluído')
    expect(rotuloDoStatus('ADIADO')).toBe('Adiado')
  })

  it('resume o perfil numa linha', () => {
    expect(
      resumoDoPerfil({ articulacoesPoupadas: [], nivel: 'INICIANTE', equipamentos: [], aceitaChao: true }),
    ).toBe('Iniciante · nada a poupar · sem equipamento · com exercícios no chão')
    expect(
      resumoDoPerfil({
        articulacoesPoupadas: ['JOELHO', 'OMBRO', 'LOMBAR'],
        nivel: 'INTERMEDIARIO',
        equipamentos: ['APOIO_DE_FLEXAO', 'HALTERES_2KG'],
        aceitaChao: false,
      }),
    ).toBe(
      'Intermediário · poupa joelho, ombro e lombar · apoio de flexão e halteres de 2 kg · sem exercícios no chão',
    )
  })

  it('dá a cada categoria o título do lembrete', () => {
    expect(tituloDoMarco('HIDRATACAO')).toBe('Hora da água 💧')
    expect(tituloDoMarco('EXERCICIO')).toBe('Hora do exercício 🏃')
  })
})
