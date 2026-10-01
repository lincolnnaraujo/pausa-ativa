package fixtures.arquitetura.r4.historico.adapter.in.web;

import fixtures.arquitetura.r4.agenda.application.port.in.ConsultarJornadas;
import fixtures.arquitetura.r4.agenda.domain.Jornada;

/** Viola a R4 ao acessar o domínio da Agenda. O uso de {@link ConsultarJornadas} é permitido. */
public class HistoricoController {

    ConsultarJornadas consultarJornadas;

    Jornada jornada;
}
