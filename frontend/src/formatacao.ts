import type { Marco, StatusMarco } from '@/api/jornada'
import type { Articulacao, Equipamento, Nivel, Perfil } from '@/api/perfil'

/** Fuso de negócio (spec H2, seção 3.4). Os horários da tela não dependem do fuso do computador. */
export const FUSO = 'America/Sao_Paulo'

const formatoDeHorario = new Intl.DateTimeFormat('pt-BR', {
  hour: '2-digit',
  minute: '2-digit',
  timeZone: FUSO,
})

const formatoDeVolume = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 })

/** HH:mm de um instante (ISO-8601 ou milissegundos), no fuso de negócio. */
export function horario(instante: string | number): string {
  return formatoDeHorario.format(new Date(instante))
}

/** Tempo trabalhado em HH:mm. 5.400 s → "01:30". */
export function duracao(segundos: number): string {
  const minutos = Math.floor(Math.max(0, segundos) / 60)
  const horas = Math.floor(minutos / 60)
  return `${String(horas).padStart(2, '0')}:${String(minutos % 60).padStart(2, '0')}`
}

/** 562.5 → "562,5 ml"; 3000 → "3.000 ml". */
export function mililitros(valor: number): string {
  return `${formatoDeVolume.format(valor)} ml`
}

/** Quanto falta para o próximo lembrete, arredondado para cima: nunca diz "em 0 min". */
export function tempoAte(segundos: number): string {
  if (segundos <= 0) {
    return 'agora'
  }
  return `em ${Math.ceil(segundos / 60)} min`
}

/** Intervalo entre lembretes: 1800 → "30 min"; 45 → "45 s". */
export function intervalo(segundos: number): string {
  return segundos % 60 === 0 ? `${segundos / 60} min` : `${segundos} s`
}

const ROTULOS: Record<StatusMarco, string> = {
  AGENDADO: 'Agendado',
  PENDENTE: 'Pendente',
  CONCLUIDO: 'Concluído',
  FALHA: 'Falha',
  NAO_ENTREGUE: 'Não entregue',
  NAO_CONCLUIDO: 'Não concluído',
  ADIADO: 'Adiado: resolvido pelo próximo bloco',
}

export function rotuloDoStatus(status: StatusMarco): string {
  return ROTULOS[status]
}

const TITULOS: Record<Marco['categoria'], string> = {
  HIDRATACAO: 'Hora da água 💧',
  EXERCICIO: 'Hora do exercício 🏃',
}

/** Título do lembrete, no cartão e na notificação. */
export function tituloDoMarco(categoria: Marco['categoria']): string {
  return TITULOS[categoria]
}

/**
 * Título da notificação de um lote de marcos: o da categoria, ou o combinado quando água e exercício
 * chegam juntos na hora cheia (spec H3, seção 3.7).
 */
export function tituloDoLembrete(marcos: Marco[]): string {
  const categorias = new Set(marcos.map((marco) => marco.categoria))
  const [categoria] = categorias
  return categorias.size === 1 && categoria !== undefined
    ? tituloDoMarco(categoria)
    : 'Hora da água e do exercício 💧🏃'
}

const CATEGORIAS: Record<Marco['categoria'], string> = {
  HIDRATACAO: '💧 Água',
  EXERCICIO: '🏃 Exercício',
}

/** Categoria com ícone, para listas e tabelas: a cor nunca é o único sinal. */
export function rotuloDaCategoria(categoria: Marco['categoria']): string {
  return CATEGORIAS[categoria]
}

/** Duração estimada do bloco: 278 → "4 min 38 s"; 300 → "5 min"; 45 → "45 s". */
export function duracaoCurta(segundos: number): string {
  const minutos = Math.floor(segundos / 60)
  const resto = segundos % 60
  if (minutos === 0) {
    return `${resto} s`
  }
  return resto === 0 ? `${minutos} min` : `${minutos} min ${resto} s`
}

export const ROTULOS_DE_ARTICULACAO: Record<Articulacao, string> = {
  JOELHO: 'Joelho',
  OMBRO: 'Ombro',
  PUNHO: 'Punho',
  LOMBAR: 'Lombar',
  CERVICAL: 'Cervical',
}

export const ROTULOS_DE_EQUIPAMENTO: Record<Equipamento, string> = {
  APOIO_DE_FLEXAO: 'Apoio de flexão',
  HALTERES_2KG: 'Halteres de 2 kg',
}

export const ROTULOS_DE_NIVEL: Record<Nivel, string> = {
  INICIANTE: 'Iniciante',
  INTERMEDIARIO: 'Intermediário',
}

const formatoDeLista = new Intl.ListFormat('pt-BR', { type: 'conjunction' })

/** Uma linha para lembrar o perfil antes de iniciar o dia: "Iniciante · poupa joelho e ombro · …". */
export function resumoDoPerfil(perfil: Perfil): string {
  const minusculas = (rotulos: string[]) => formatoDeLista.format(rotulos.map((r) => r.toLowerCase()))
  const articulacoes = perfil.articulacoesPoupadas.map((a) => ROTULOS_DE_ARTICULACAO[a])
  const equipamentos = perfil.equipamentos.map((e) => ROTULOS_DE_EQUIPAMENTO[e])
  return [
    ROTULOS_DE_NIVEL[perfil.nivel],
    articulacoes.length > 0 ? `poupa ${minusculas(articulacoes)}` : 'nada a poupar',
    equipamentos.length > 0 ? minusculas(equipamentos) : 'sem equipamento',
    perfil.aceitaChao ? 'com exercícios no chão' : 'sem exercícios no chão',
  ].join(' · ')
}
