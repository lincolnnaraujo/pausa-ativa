package br.com.pausaativa.agenda.application.port.in;

import java.util.UUID;

public interface RetomarJornada {

    SituacaoDaJornada retomar(UUID jornadaId);
}
