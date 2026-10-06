import { readdirSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { describe, expect, it } from 'vitest'

/**
 * Identidade visual "Sereno & Balanceado" (spec H3, seção 8.1; Cenário 8): a paleta da definição, só
 * tema escuro e contraste mínimo do WCAG AA. As cores ficam todas nos tokens do main.css.
 *
 * Os arquivos são lidos do disco: o Vitest entrega CSS importado vazio, mesmo com ?raw.
 */

const SRC = resolve(import.meta.dirname, '..')
const css = readFileSync(`${SRC}/assets/main.css`, 'utf8')
const componentes = readdirSync(SRC, { recursive: true, encoding: 'utf8' })
  .filter((arquivo) => arquivo.endsWith('.vue'))
  .map((arquivo) => ({ arquivo, fonte: readFileSync(`${SRC}/${arquivo}`, 'utf8') }))

/** Tokens declarados no :root, com os var(--outro) resolvidos. */
function tokens(): Map<string, string> {
  const declarados = new Map(
    [...css.matchAll(/--([a-z-]+):\s*([^;]+);/g)].map(([, nome, valor]) => [
      nome as string,
      (valor as string).trim(),
    ]),
  )
  const resolver = (valor: string): string => {
    const referencia = /^var\(--([a-z-]+)\)$/.exec(valor)
    return referencia ? resolver(declarados.get(referencia[1] as string) ?? valor) : valor
  }
  return new Map([...declarados].map(([nome, valor]) => [nome, resolver(valor)]))
}

const cor = (nome: string): string => {
  const valor = tokens().get(nome)
  if (valor === undefined) {
    throw new Error(`Token --${nome} não existe no main.css`)
  }
  return valor
}

/** Luminância relativa (WCAG 2.2). */
function luminancia(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const canal = parseInt(hex.slice(i, i + 2), 16) / 255
    return canal <= 0.03928 ? canal / 12.92 : ((canal + 0.055) / 1.055) ** 2.4
  }) as [number, number, number]
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

function contraste(frente: string, fundo: string): number {
  const [clara, escura] = [luminancia(frente), luminancia(fundo)].sort((a, b) => b - a) as [
    number,
    number,
  ]
  return (clara + 0.05) / (escura + 0.05)
}

describe('identidade visual', () => {
  it('usa as cinco cores da definição', () => {
    expect(cor('fundo')).toBe('#1a2238')
    expect(cor('destaque')).toBe('#20b2aa')
    expect(cor('agua')).toBe('#87ceeb')
    expect(cor('alerta')).toBe('#ff8c69')
    expect(cor('texto')).toBe('#f0f8ff')
  })

  it('dá a cada papel a cor certa: exercício e sucesso em teal, erro e aviso em coral', () => {
    expect(cor('exercicio')).toBe(cor('destaque'))
    expect(cor('sucesso')).toBe(cor('destaque'))
    expect(cor('erro')).toBe(cor('alerta'))
    expect(cor('aviso')).toBe(cor('alerta'))
    expect(cor('texto-sobre-destaque')).toBe(cor('fundo'))
  })

  it('tem só o tema escuro (D10)', () => {
    expect(css).toContain('color-scheme: dark;')
    expect(css).not.toContain('prefers-color-scheme')
  })

  it.each([
    ['texto', 'fundo'],
    ['texto', 'fundo-cartao'],
    ['texto', 'fundo-alerta'],
    ['texto-suave', 'fundo'],
    ['texto-suave', 'fundo-cartao'],
    ['destaque', 'fundo'],
    ['destaque', 'fundo-cartao'],
    ['agua', 'fundo'],
    ['agua', 'fundo-cartao'],
    ['alerta', 'fundo'],
    ['alerta', 'fundo-cartao'],
    ['alerta', 'fundo-alerta'],
    ['texto-sobre-destaque', 'destaque'],
    ['texto-sobre-destaque', 'destaque-realce'],
  ])('texto em --%s sobre --%s tem pelo menos 4,5:1', (frente, fundo) => {
    expect(contraste(cor(frente), cor(fundo))).toBeGreaterThanOrEqual(4.5)
  })

  it('o texto gelo não serve sobre o teal: por isso o botão usa azul-escuro', () => {
    expect(contraste(cor('texto'), cor('destaque'))).toBeLessThan(4.5)
  })

  it('a borda dos campos tem pelo menos 3:1 com o cartão (WCAG 1.4.11)', () => {
    expect(contraste(cor('borda-campo'), cor('fundo-cartao'))).toBeGreaterThanOrEqual(3)
  })

  it('nenhum componente tem cor fixa: todas vêm dos tokens', () => {
    const comCorFixa = componentes
      .filter(({ fonte }) => /#[0-9a-f]{3,8}\b|rgba?\(|hsla?\(/i.test(fonte))
      .map(({ arquivo }) => arquivo)

    expect(componentes.length).toBeGreaterThan(5)
    expect(comCorFixa).toEqual([])
  })
})
