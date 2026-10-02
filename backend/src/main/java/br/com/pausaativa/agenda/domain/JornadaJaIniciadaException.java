package br.com.pausaativa.agenda.domain;

import java.time.LocalDate;

/** Uma jornada por dia (decisão D3): já há jornada aberta, ou a de hoje já foi finalizada. */
public class JornadaJaIniciadaException extends RegraDeNegocioException {

    public JornadaJaIniciadaException(LocalDate dia) {
        super("Já existe uma jornada para %s. Cada dia tem uma jornada só, e uma jornada finalizada não reabre."
                .formatted(dia));
    }
}
