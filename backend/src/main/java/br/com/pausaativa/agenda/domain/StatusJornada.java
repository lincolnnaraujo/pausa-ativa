package br.com.pausaativa.agenda.domain;

public enum StatusJornada {
    EM_ANDAMENTO,
    PAUSADA,
    FINALIZADA,
    ENCERRADA_AUTOMATICAMENTE;

    /** Jornada que ainda aceita pausa, retomada, finalização e disparo de marcos. */
    public boolean aberta() {
        return this == EM_ANDAMENTO || this == PAUSADA;
    }
}
