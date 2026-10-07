import { enableAutoUnmount, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { umaContagem, umaSemana, umDia, umResumo } from '@/__tests__/fabrica'
import type { Categoria, Historico } from '@/api/historico'

import GraficoDoPeriodo from '../GraficoDoPeriodo.vue'

enableAutoUnmount(afterEach)

function montar(categoria: Categoria = 'HIDRATACAO', historico: Historico = umaSemana()) {
  return mount(GraficoDoPeriodo, {
    props: {
      resumo: historico.categorias.find((resumo) => resumo.categoria === categoria)!,
      dias: historico.dias,
      periodo: historico.periodo,
      rotuloDoPeriodo: '5 – 11 out 2026',
    },
  })
}

function coluna(wrapper: VueWrapper, data: string) {
  return wrapper.get(`g[data-data="${data}"]`)
}

function faixa(wrapper: VueWrapper, data: string) {
  return wrapper.get(`button[data-data="${data}"]`)
}

/** Outubro de 2026 até hoje, dia 7, com jornada nos dias úteis. */
function outubro(): Historico {
  const dias = Array.from({ length: 31 }, (_, i) => {
    const data = `2026-10-${String(i + 1).padStart(2, '0')}`
    return i + 1 > 7
      ? umDia(data, { futuro: true })
      : umDia(data, {
          jornada: 'FINALIZADA',
          categorias: [umaContagem('HIDRATACAO', { concluidos: 14, falhas: 2, taxa: 87.5, metaAtingida: true })],
        })
  })
  return {
    periodo: 'MES',
    inicio: '2026-10-01',
    fim: '2026-10-31',
    categorias: [umResumo('HIDRATACAO', { concluidos: 98, falhas: 14, taxa: 87.5, metaAtingida: true })],
    dias,
  }
}

describe('GraficoDoPeriodo', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('é uma imagem com título e descrição para o leitor de tela', () => {
    const wrapper = montar()

    const svg = wrapper.get('svg')
    expect(svg.attributes('role')).toBe('img')
    const [titulo, descricao] = svg.attributes('aria-labelledby')!.split(' ')
    expect(wrapper.get(`[id="${titulo}"]`).text()).toBe('Água, 5 – 11 out 2026')
    expect(wrapper.get(`[id="${descricao}"]`).text()).toBe(
      '30 concluídos, 4 falhas, 2 não entregues e 0 não concluídos; taxa de 88,2%.',
    )
  })

  it('a legenda fica sempre visível, com as quatro situações e a marca da meta', () => {
    const wrapper = montar()

    expect(wrapper.get('h3').text()).toBe('💧 Água por dia')
    expect(wrapper.findAll('.legenda li').map((li) => li.text())).toEqual([
      'Concluído',
      'Falha',
      'Não entregue',
      'Não concluído',
      '✓Dia na meta de 80%',
    ])
  })

  it('o eixo vai até 16 na água e até 8 no exercício', () => {
    expect(montar('HIDRATACAO').findAll('.rotulo-y').map((rotulo) => rotulo.text())).toEqual([
      '0',
      '4',
      '8',
      '12',
      '16',
    ])
    expect(montar('EXERCICIO').findAll('.rotulo-y').map((rotulo) => rotulo.text())).toEqual([
      '0',
      '2',
      '4',
      '6',
      '8',
    ])
  })

  it('empilha as situações do dia, com o topo arredondado, e marca com ✓ o dia na meta', () => {
    const wrapper = montar()

    const segunda = coluna(wrapper, '2026-10-05')
    expect(
      segunda.findAll('.segmento').map((segmento) => [
        segmento.element.tagName,
        segmento.attributes('data-situacao'),
        segmento.attributes('data-quantidade'),
      ]),
    ).toEqual([
      ['rect', 'concluidos', '12'],
      ['rect', 'falhas', '2'],
      ['path', 'naoEntregues', '2'],
    ])
    expect(segunda.get('.na-meta').text()).toBe('✓')
    expect(coluna(wrapper, '2026-10-07').find('.na-meta').exists()).toBe(false)
    expect(coluna(wrapper, '2026-10-07').findAll('.segmento')).toHaveLength(2)
  })

  it('dias futuros ficam vazios, e só os dias com jornada são interativos', () => {
    const wrapper = montar()

    expect(coluna(wrapper, '2026-10-09').classes()).toContain('futuro')
    expect(coluna(wrapper, '2026-10-09').findAll('.segmento')).toHaveLength(0)
    expect(wrapper.findAll('button.faixa').map((botao) => botao.attributes('data-data'))).toEqual([
      '2026-10-05',
      '2026-10-06',
      '2026-10-07',
    ])
    expect(faixa(wrapper, '2026-10-05').attributes('aria-label')).toBe(
      'seg 05/10: taxa de 85,7%, meta atingida. Concluído 12, falha 2, não entregue 2, não concluído 0. Abrir o dia.',
    )
  })

  it('a dica de valores aparece com o mouse e some quando ele sai', async () => {
    const wrapper = montar()

    await faixa(wrapper, '2026-10-05').trigger('mouseenter')

    const dica = wrapper.get('[data-testid="dica"]')
    expect(dica.get('.dica-dia').text()).toBe('seg 05/10')
    expect(dica.get('.dica-taxa').text()).toBe('85,7% ✓ na meta')
    expect(dica.findAll('li').map((li) => li.text())).toEqual([
      'Concluído: 12',
      'Falha: 2',
      'Não entregue: 2',
      'Não concluído: 0',
    ])
    expect(coluna(wrapper, '2026-10-05').classes()).toContain('em-foco')

    await faixa(wrapper, '2026-10-05').trigger('mouseleave')
    expect(wrapper.find('[data-testid="dica"]').exists()).toBe(false)
  })

  it('a dica também vem com o foco do teclado, e o Esc a fecha', async () => {
    const wrapper = montar()

    await faixa(wrapper, '2026-10-07').trigger('focus')
    const dica = wrapper.get('[data-testid="dica"]')
    expect(dica.get('.dica-dia').text()).toBe('qua 07/10 · em andamento')
    expect(dica.get('.dica-taxa').text()).toBe('75,0%')
    expect(dica.text()).toContain('Em aberto: 12')

    await faixa(wrapper, '2026-10-07').trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('[data-testid="dica"]').exists()).toBe(false)

    await faixa(wrapper, '2026-10-06').trigger('focus')
    await faixa(wrapper, '2026-10-06').trigger('blur')
    expect(wrapper.find('[data-testid="dica"]').exists()).toBe(false)
  })

  it('a dica não passa das bordas do gráfico', async () => {
    const wrapper = montar('HIDRATACAO', outubro())

    await faixa(wrapper, '2026-10-01').trigger('mouseenter')

    expect(wrapper.get('[data-testid="dica"]').attributes('style')).toContain('left: 112px')
  })

  it('a dica de um dia sem taxa diz "sem dados"', async () => {
    const semana = umaSemana()
    semana.dias[0] = umDia('2026-10-05', {
      jornada: 'FINALIZADA',
      categorias: [umaContagem('HIDRATACAO', { naoEntregues: 16 })],
    })
    const wrapper = montar('HIDRATACAO', semana)

    await faixa(wrapper, '2026-10-05').trigger('mouseenter')

    expect(wrapper.get('.dica-taxa').text()).toBe('sem dados')
  })

  it('clicar na faixa do dia abre o dia', async () => {
    const wrapper = montar()

    await faixa(wrapper, '2026-10-06').trigger('click')

    expect(wrapper.emitted('abrirDia')).toEqual([['2026-10-06']])
  })

  it('mede a caixa: no celular, o mês cabe com colunas finas e um rótulo a cada cinco dias', async () => {
    let avisar: ResizeObserverCallback = () => {}
    const desligar = vi.fn()
    vi.stubGlobal(
      'ResizeObserver',
      class {
        constructor(callback: ResizeObserverCallback) {
          avisar = callback
        }
        observe() {}
        disconnect = desligar
      },
    )
    const wrapper = montar('HIDRATACAO', outubro())

    avisar([{ contentRect: { width: 352 } } as ResizeObserverEntry], {} as ResizeObserver)
    await wrapper.vm.$nextTick()

    expect(wrapper.get('svg').attributes('viewBox')).toBe('0 0 352 200')
    expect(wrapper.findAll('.rotulo-x').map((rotulo) => rotulo.text())).toEqual([
      '1',
      '5',
      '10',
      '15',
      '20',
      '25',
      '30',
    ])
    expect(Number(coluna(wrapper, '2026-10-01').get('rect').attributes('width'))).toBeLessThan(10)

    avisar([{ contentRect: { width: 0 } } as ResizeObserverEntry], {} as ResizeObserver)
    await wrapper.vm.$nextTick()
    expect(wrapper.get('svg').attributes('viewBox')).toBe('0 0 352 200')

    wrapper.unmount()
    expect(desligar).toHaveBeenCalled()
  })
})
