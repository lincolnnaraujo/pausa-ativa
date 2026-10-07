package br.com.pausaativa.agenda.application.port.in;

import br.com.pausaativa.agenda.domain.Categoria;
import br.com.pausaativa.agenda.domain.Marco;
import br.com.pausaativa.agenda.domain.StatusMarco;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Retrato de um marco para quem está fora do domínio. Horários no fuso de negócio.
 *
 * @param volumeMl nulo no exercício
 * @param volumeAproximadoMl nulo no exercício
 * @param podeAdiar se o botão Adiar vale agora: calculado aqui para a tela não repetir a regra
 * @param bloco nulo na água e no exercício que ainda não disparou
 */
public record SituacaoDoMarco(
        UUID id,
        Categoria categoria,
        int sequencia,
        StatusMarco status,
        BigDecimal volumeMl,
        Integer volumeAproximadoMl,
        long segundosTrabalhadosPrevistos,
        OffsetDateTime disparadoEm,
        OffsetDateTime recebidoEm,
        OffsetDateTime respondidoEm,
        OffsetDateTime editadoEm,
        String mensagem,
        boolean podeAdiar,
        boolean podeCorrigir,
        SituacaoDoBloco bloco) {

    static SituacaoDoMarco de(Marco marco, boolean podeAdiar, boolean podeCorrigir, ZoneId fuso) {
        return new SituacaoDoMarco(
                marco.id(),
                marco.categoria(),
                marco.sequencia(),
                marco.status(),
                marco.volumeMl(),
                marco.volumeMl() == null ? null : marco.volumeArredondado(),
                marco.tempoTrabalhadoPrevisto().toSeconds(),
                noFuso(marco.disparadoEm().orElse(null), fuso),
                noFuso(marco.recebidoEm().orElse(null), fuso),
                noFuso(marco.respondidoEm().orElse(null), fuso),
                noFuso(marco.editadoEm().orElse(null), fuso),
                marco.mensagem(),
                podeAdiar,
                podeCorrigir,
                marco.bloco().map(SituacaoDoBloco::de).orElse(null));
    }

    static OffsetDateTime noFuso(Instant instante, ZoneId fuso) {
        return instante == null ? null : instante.atZone(fuso).toOffsetDateTime();
    }
}
