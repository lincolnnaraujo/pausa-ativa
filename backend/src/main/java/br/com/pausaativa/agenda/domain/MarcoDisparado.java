package br.com.pausaativa.agenda.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** O marco virou {@code PENDENTE}: hora de notificar o usuário. */
public record MarcoDisparado(
        UUID marcoId, Categoria categoria, int sequencia, Instant previstoPara, Instant disparadoEm)
        implements EventoDaJornada {

    /** Quanto o disparo atrasou em relação ao instante calculado (requisito: até 5 s). */
    public Duration atraso() {
        return Duration.between(previstoPara, disparadoEm);
    }
}
