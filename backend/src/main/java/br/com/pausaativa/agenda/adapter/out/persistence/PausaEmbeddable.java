package br.com.pausaativa.agenda.adapter.out.persistence;

import br.com.pausaativa.agenda.domain.Pausa;
import jakarta.persistence.Embeddable;
import java.time.Instant;

@Embeddable
record PausaEmbeddable(Instant inicio, Instant fim) {

    static PausaEmbeddable de(Pausa pausa) {
        return new PausaEmbeddable(pausa.inicio(), pausa.fim().orElse(null));
    }

    Pausa paraDominio() {
        return Pausa.reconstituir(inicio, fim);
    }
}
