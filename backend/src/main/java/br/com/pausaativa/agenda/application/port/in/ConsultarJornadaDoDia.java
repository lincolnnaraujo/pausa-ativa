package br.com.pausaativa.agenda.application.port.in;

import java.time.LocalDate;
import java.util.Optional;

/** A jornada de um dia, passado ou de hoje, com os blocos propostos (spec H4, seção 6). */
public interface ConsultarJornadaDoDia {

    /** Vazio se não houve jornada nesse dia. */
    Optional<SituacaoDaJornada> doDia(LocalDate dia);
}
