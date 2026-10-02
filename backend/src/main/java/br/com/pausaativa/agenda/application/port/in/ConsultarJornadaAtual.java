package br.com.pausaativa.agenda.application.port.in;

import java.util.Optional;

public interface ConsultarJornadaAtual {

    /** A jornada aberta; sem ela, a de hoje (já encerrada); sem nenhuma, vazio. */
    Optional<SituacaoDaJornada> consultar();
}
