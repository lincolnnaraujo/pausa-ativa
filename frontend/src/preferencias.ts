import {
  DURACAO_PADRAO_DO_BLOCO_MIN,
  DURACOES_DO_BLOCO_MIN,
  type DuracaoDoBlocoMin,
  META_MAXIMA_ML,
  META_PADRAO_ML,
} from '@/api/jornada'

const CHAVE_META = 'pausa-ativa.meta-agua-ml'
const CHAVE_DURACAO = 'pausa-ativa.duracao-bloco-min'
const CHAVE_SOM = 'pausa-ativa.som'

/** Meta válida: inteiro de 1 a 6.000 ml (spec H2, seção 3.4). */
export function metaValida(meta: number): boolean {
  return Number.isInteger(meta) && meta >= 1 && meta <= META_MAXIMA_ML
}

/** A meta usada no último dia, para preencher o campo; sem ela, 3.000 ml. */
export function lerUltimaMeta(): number {
  const salva = Number(ler(CHAVE_META))
  return metaValida(salva) ? salva : META_PADRAO_ML
}

export function guardarUltimaMeta(meta: number): void {
  guardar(CHAVE_META, String(meta))
}

/** A duração do bloco escolhida no último dia (spec H3, seção 8); sem ela, 5 min. */
export function lerUltimaDuracao(): DuracaoDoBlocoMin {
  const salva = Number(ler(CHAVE_DURACAO))
  return DURACOES_DO_BLOCO_MIN.find((duracao) => duracao === salva) ?? DURACAO_PADRAO_DO_BLOCO_MIN
}

export function guardarUltimaDuracao(duracao: DuracaoDoBlocoMin): void {
  guardar(CHAVE_DURACAO, String(duracao))
}

/** Som dos lembretes: ligado, a menos que a pessoa tenha desligado (spec H2, D7). */
export function lerSomLigado(): boolean {
  return ler(CHAVE_SOM) !== 'desligado'
}

export function guardarSomLigado(ligado: boolean): void {
  guardar(CHAVE_SOM, ligado ? 'ligado' : 'desligado')
}

/** Sem armazenamento no navegador (modo restrito), vale o padrão. */
function ler(chave: string): string | null {
  try {
    return localStorage.getItem(chave)
  } catch {
    return null
  }
}

function guardar(chave: string, valor: string): void {
  try {
    localStorage.setItem(chave, valor)
  } catch {
    // A preferência volta ao padrão na próxima visita. Nada a fazer.
  }
}
