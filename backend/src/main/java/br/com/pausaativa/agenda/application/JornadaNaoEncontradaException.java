package br.com.pausaativa.agenda.application;

import br.com.pausaativa.agenda.domain.RegraDeNegocioException;
import java.util.UUID;

public class JornadaNaoEncontradaException extends RegraDeNegocioException {

    JornadaNaoEncontradaException(UUID jornadaId) {
        super("Jornada não encontrada: " + jornadaId);
    }
}
