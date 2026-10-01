package fixtures.arquitetura.r6.agenda.domain;

import java.time.Instant;

/** Viola a R6: lê o relógio do sistema em vez de receber o instante ou um Clock. */
public class MarcoComRelogioDoSistema {

    Instant disparadoEm = Instant.now();
}
