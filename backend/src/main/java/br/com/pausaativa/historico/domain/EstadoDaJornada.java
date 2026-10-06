package br.com.pausaativa.historico.domain;

/** Estado da jornada de um dia, para o histórico marcar o dia em andamento (decisão D11 da spec H4). */
public enum EstadoDaJornada {
    EM_ANDAMENTO,
    PAUSADA,
    FINALIZADA,
    ENCERRADA_AUTOMATICAMENTE
}
