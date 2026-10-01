package br.com.pausaativa.agenda.domain;

import java.util.UUID;

public class MarcoNaoEncontradoException extends RegraDeNegocioException {

    MarcoNaoEncontradoException(UUID marcoId) {
        super("Lembrete não encontrado: " + marcoId);
    }
}
