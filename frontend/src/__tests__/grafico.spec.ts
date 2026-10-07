import { describe, expect, it } from 'vitest'

import { umaContagem, umaSemana, umDia, umResumo } from '@/__tests__/fabrica'
import {
  ALTURA,
  descricaoDoDia,
  descricaoDoGrafico,
  geometria,
  MARGEM,
  tituloDoGrafico,
  topoArredondado,
} from '@/grafico'

/** Os 31 dias de outubro de 2026, sem jornada. */
const OUTUBRO = Array.from({ length: 31 }, (_, i) => umDia(`2026-10-${String(i + 1).padStart(2, '0')}`))

describe('geometria dos gráficos', () => {
  const BASE = ALTURA - MARGEM.base // 166: a área útil vai de 18 a 166, 148 px

  it('cada categoria tem a sua escala fixa, com marcas redondas', () => {
    const agua = geometria(umaSemana().dias, 'HIDRATACAO', 'SEMANA', 526)
    const exercicio = geometria(umaSemana().dias, 'EXERCICIO', 'SEMANA', 526)

    expect(agua.marcas).toEqual([
      { valor: 0, y: BASE },
      { valor: 4, y: 129 },
      { valor: 8, y: 92 },
      { valor: 12, y: 55 },
      { valor: 16, y: MARGEM.topo },
    ])
    expect(exercicio.marcas.map((marca) => marca.valor)).toEqual([0, 2, 4, 6, 8])
    expect(exercicio.marcas.at(-1)!.y).toBe(MARGEM.topo)
  })

  it('empilha de baixo para cima concluído, falha, não entregue e não concluído, com 2 px entre eles', () => {
    const segunda = geometria(umaSemana().dias, 'HIDRATACAO', 'SEMANA', 526).colunas[0]!

    expect(
      segunda.segmentos.map(({ situacao, quantidade, y, altura, topo }) => [
        situacao.chave,
        quantidade,
        y,
        altura,
        topo,
      ]),
    ).toEqual([
      ['concluidos', 12, 55, 111, false],
      ['falhas', 2, 36.5, 16.5, false],
      ['naoEntregues', 2, 18, 16.5, true],
    ])
    expect(segunda.topoY).toBe(18)
  })

  it('a coluna tem no máximo 24 px, centrada na faixa do dia, que é a área de toque', () => {
    const { colunas } = geometria(umaSemana().dias, 'HIDRATACAO', 'SEMANA', 526)

    expect(colunas[0]).toMatchObject({ faixaX: MARGEM.esquerda, faixaLargura: 70.57, x: 51.29, largura: 24 })
    expect(colunas[1]!.faixaX).toBe(98.57)
    expect(colunas[0]!.centro).toBe(63.29)
  })

  it('dia sem jornada e dia futuro ficam sem coluna', () => {
    const semana = umaSemana()
    semana.dias[1] = umDia('2026-10-06')
    const { colunas } = geometria(semana.dias, 'HIDRATACAO', 'SEMANA', 526)

    expect(colunas[1]!.segmentos).toEqual([])
    expect(colunas[1]!.contagem).toBeUndefined()
    expect(colunas[6]!.segmentos).toEqual([])
    expect(colunas[6]!.topoY).toBe(BASE)
  })

  it('a categoria sem lembretes no dia não ganha coluna, mas o dia continua com contagem', () => {
    const dia = umDia('2026-09-10', {
      jornada: 'FINALIZADA',
      categorias: [umaContagem('HIDRATACAO', { concluidos: 16 }), umaContagem('EXERCICIO')],
    })

    const [coluna] = geometria([dia], 'EXERCICIO', 'SEMANA', 526).colunas

    expect(coluna!.segmentos).toEqual([])
    expect(coluna!.contagem).toEqual(umaContagem('EXERCICIO'))
  })

  it('na semana, o eixo mostra o dia da semana e o número', () => {
    const { colunas } = geometria(umaSemana().dias, 'HIDRATACAO', 'SEMANA', 526)

    expect(colunas.map((coluna) => coluna.rotulo)).toEqual([
      ['seg', '5'],
      ['ter', '6'],
      ['qua', '7'],
      ['qui', '8'],
      ['sex', '9'],
      ['sáb', '10'],
      ['dom', '11'],
    ])
  })

  it('no mês estreito, o eixo mostra o dia 1 e os múltiplos de 5; com espaço, todos', () => {
    const estreito = geometria(OUTUBRO, 'HIDRATACAO', 'MES', 526).colunas
    const largo = geometria(OUTUBRO, 'HIDRATACAO', 'MES', 1_000).colunas

    expect(estreito.flatMap((coluna) => coluna.rotulo)).toEqual(['1', '5', '10', '15', '20', '25', '30'])
    expect(largo.flatMap((coluna) => coluna.rotulo)).toHaveLength(31)
  })

  it('no celular, as colunas do mês ficam mais finas', () => {
    const [primeira] = geometria(OUTUBRO, 'HIDRATACAO', 'MES', 352).colunas

    expect(primeira!.largura).toBeLessThan(10)
    expect(primeira!.faixaLargura).toBe(10.32)
  })

  it('arredonda os cantos de cima e deixa a base reta', () => {
    expect(topoArredondado(10, 20, 24, 50)).toBe('M10,70V24Q10,20 14,20H30Q34,20 34,24V70Z')
    // Segmento baixo: o raio não passa da altura.
    expect(topoArredondado(10, 20, 24, 2)).toBe('M10,22V22Q10,20 12,20H32Q34,20 34,22V22Z')
  })
})

describe('textos dos gráficos', () => {
  it('descreve o dia com a taxa, a meta e as quantidades de cada situação', () => {
    const [segunda, , quarta] = umaSemana().dias

    expect(descricaoDoDia(segunda!, segunda!.categorias[0]!)).toBe(
      'seg 05/10: taxa de 85,7%, meta atingida. Concluído 12, falha 2, não entregue 2, não concluído 0.',
    )
    expect(descricaoDoDia(quarta!, quarta!.categorias[0]!)).toBe(
      'qua 07/10, em andamento: taxa de 75,0%, meta não atingida. ' +
        'Concluído 3, falha 1, não entregue 0, não concluído 0, em aberto 12.',
    )
  })

  it('o dia sem resposta na categoria diz "sem dados"', () => {
    const dia = umDia('2026-09-10', { jornada: 'FINALIZADA', categorias: [umaContagem('EXERCICIO')] })

    expect(descricaoDoDia(dia, dia.categorias[0]!)).toBe(
      'qui 10/09: sem dados. Concluído 0, falha 0, não entregue 0, não concluído 0.',
    )
  })

  it('o título e a descrição do gráfico, para o leitor de tela', () => {
    const [agua] = umaSemana().categorias

    expect(tituloDoGrafico('HIDRATACAO', '28 set – 4 out 2026')).toBe('Água, 28 set – 4 out 2026')
    expect(tituloDoGrafico('EXERCICIO', 'outubro de 2026')).toBe('Exercício, outubro de 2026')
    expect(descricaoDoGrafico(agua!)).toBe('30 concluídos, 4 falhas, 2 não entregues e 0 não concluídos; taxa de 88,2%.')
    expect(descricaoDoGrafico(umResumo('EXERCICIO'))).toBe(
      '0 concluídos, 0 falhas, 0 não entregues e 0 não concluídos; sem dados.',
    )
  })
})
