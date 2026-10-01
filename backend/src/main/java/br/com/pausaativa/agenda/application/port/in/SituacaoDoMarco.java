package br.com.pausaativa.agenda.application.port.in;

import br.com.pausaativa.agenda.domain.Categoria;
import br.com.pausaativa.agenda.domain.Marco;
import br.com.pausaativa.agenda.domain.StatusMarco;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/** Retrato de um marco para quem está fora do domínio. Horários no fuso de negócio. */
public record SituacaoDoMarco(
        UUID id,
        Categoria categoria,
        int sequencia,
        StatusMarco status,
        BigDecimal volumeMl,
        long segundosTrabalhadosPrevistos,
        OffsetDateTime disparadoEm,
        OffsetDateTime recebidoEm,
        OffsetDateTime respondidoEm,
        String mensagem) {

    public static SituacaoDoMarco de(Marco marco, ZoneId fuso) {
        return new SituacaoDoMarco(
                marco.id(),
                marco.categoria(),
                marco.sequencia(),
                marco.status(),
                marco.volumeMl(),
                marco.tempoTrabalhadoPrevisto().toSeconds(),
                noFuso(marco.disparadoEm().orElse(null), fuso),
                noFuso(marco.recebidoEm().orElse(null), fuso),
                noFuso(marco.respondidoEm().orElse(null), fuso),
                marco.mensagem());
    }

    static OffsetDateTime noFuso(Instant instante, ZoneId fuso) {
        return instante == null ? null : instante.atZone(fuso).toOffsetDateTime();
    }
}
