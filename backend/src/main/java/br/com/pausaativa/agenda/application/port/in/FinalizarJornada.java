package br.com.pausaativa.agenda.application.port.in;

import java.util.UUID;

public interface FinalizarJornada {

    /** Idempotente: finalizar de novo devolve a mesma situação. */
    SituacaoDaJornada finalizar(UUID jornadaId);
}
