package br.com.pausaativa.agenda.application.port.in;

import br.com.pausaativa.agenda.domain.Jornada;
import br.com.pausaativa.agenda.domain.Marco;
import br.com.pausaativa.agenda.domain.StatusJornada;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Retrato da jornada num instante. {@code tempoTrabalhadoSegundos} vale para {@code calculadoEm}; a
 * tela soma o tempo decorrido desde então enquanto a jornada estiver em andamento.
 *
 * @param marcos em ordem de horário; na hora cheia, a água vem antes do exercício
 */
public record SituacaoDaJornada(
        UUID id,
        LocalDate dataReferencia,
        StatusJornada status,
        OffsetDateTime iniciadaEm,
        OffsetDateTime finalizadaEm,
        OffsetDateTime pausadaDesde,
        long tempoTrabalhadoSegundos,
        OffsetDateTime calculadoEm,
        int metaAguaMl,
        int duracaoBlocoMin,
        BigDecimal aguaIngeridaMl,
        List<SituacaoDoMarco> marcos) {

    private static final Comparator<Marco> POR_HORARIO =
            Comparator.comparing(Marco::tempoTrabalhadoPrevisto).thenComparing(Marco::categoria);

    public static SituacaoDaJornada de(Jornada jornada, Instant agora, ZoneId fuso) {
        return new SituacaoDaJornada(
                jornada.id(),
                jornada.dataReferencia(),
                jornada.status(),
                SituacaoDoMarco.noFuso(jornada.iniciadaEm(), fuso),
                SituacaoDoMarco.noFuso(jornada.finalizadaEm().orElse(null), fuso),
                SituacaoDoMarco.noFuso(jornada.pausadaDesde().orElse(null), fuso),
                jornada.tempoTrabalhado(agora).toSeconds(),
                SituacaoDoMarco.noFuso(agora, fuso),
                jornada.meta().mililitros(),
                jornada.duracaoDoBloco().minutos(),
                jornada.aguaIngeridaMl(),
                jornada.marcos().stream()
                        .sorted(POR_HORARIO)
                        .map(marco -> SituacaoDoMarco.de(marco, jornada.podeAdiar(marco), fuso))
                        .toList());
    }
}
