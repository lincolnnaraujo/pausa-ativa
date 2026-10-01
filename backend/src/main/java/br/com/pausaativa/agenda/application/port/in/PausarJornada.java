package br.com.pausaativa.agenda.application.port.in;

import java.util.UUID;

public interface PausarJornada {

    SituacaoDaJornada pausar(UUID jornadaId);
}
