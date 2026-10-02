import type { Marco, StatusMarco } from '@/api/jornada'

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
}

export function rotuloDoStatus(status: StatusMarco): string {
  return ROTULOS[status]
}

const TITULOS: Record<Marco['categoria'], string> = {
  HIDRATACAO: 'Hora da água 💧',
}

/** Título do lembrete, no cartão e na notificação. */
export function tituloDoMarco(categoria: Marco['categoria']): string {
  return TITULOS[categoria]
}
