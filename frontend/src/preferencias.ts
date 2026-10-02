import { META_MAXIMA_ML, META_PADRAO_ML } from '@/api/jornada'

const CHAVE_META = 'pausa-ativa.meta-agua-ml'

/** Meta válida: inteiro de 1 a 6.000 ml (spec H2, seção 3.4). */
export function metaValida(meta: number): boolean {
  return Number.isInteger(meta) && meta >= 1 && meta <= META_MAXIMA_ML
}

/** A meta usada no último dia, para preencher o campo; sem ela, 3.000 ml. */
export function lerUltimaMeta(): number {
  try {
    const salva = Number(localStorage.getItem(CHAVE_META))
    return metaValida(salva) ? salva : META_PADRAO_ML
  } catch {
    // Navegador sem armazenamento (modo restrito): vale o padrão.
    return META_PADRAO_ML
  }
}

export function guardarUltimaMeta(meta: number): void {
  try {
    localStorage.setItem(CHAVE_META, String(meta))
  } catch {
    // Sem armazenamento, o campo volta ao padrão no dia seguinte. Nada a fazer.
  }
}
