package br.com.pausaativa.historico.domain;

/**
 * Situação de um lembrete para o histórico (spec H4, seção 3.1). Agendado, pendente e adiado ainda sem
 * destino chegam juntos como {@link #EM_ABERTO}: só existem na jornada em andamento.
 */
public enum Situacao {
    CONCLUIDO,
    FALHA,
    NAO_ENTREGUE,
    NAO_CONCLUIDO,
    EM_ABERTO
}
