package fixtures.arquitetura.r6.agenda.domain;

import java.time.Clock;
import java.time.Instant;

/** Permitido pela R6: o instante vem de um Clock. */
public class MarcoComClock {

    Instant disparadoEm(Clock clock) {
        return Instant.now(clock);
    }
}
