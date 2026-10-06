package br.com.pausaativa.agenda.adapter.in.web;

import br.com.pausaativa.agenda.application.port.in.SituacaoDaJornada;
import br.com.pausaativa.agenda.domain.StatusJornada;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(
        name = "Jornada",
        description = "Situação da jornada no instante calculadoEm. Horários no fuso America/Sao_Paulo.",
        requiredProperties = {
            "id",
            "dataReferencia",
            "status",
            "iniciadaEm",
            "finalizadaEm",
            "pausadaDesde",
            "tempoTrabalhadoSegundos",
            "calculadoEm",
            "metaAguaMl",
            "duracaoBlocoMin",
            "aguaIngeridaMl",
            "marcos"
        })
record JornadaResposta(
        UUID id,

        @Schema(description = "Dia em que a jornada começou")
        LocalDate dataReferencia,

        StatusJornada status,
        OffsetDateTime iniciadaEm,

        @Schema(
                types = {"string", "null"},
                format = "date-time")
        OffsetDateTime finalizadaEm,

        @Schema(
                description = "Início da pausa em curso; nulo fora da pausa",
                types = {"string", "null"},
                format = "date-time")
        OffsetDateTime pausadaDesde,

        @Schema(
                description = "Tempo trabalhado até calculadoEm, sem as pausas. Enquanto a jornada"
                        + " estiver em andamento, a tela soma o tempo decorrido desde então.",
                example = "5400")
        long tempoTrabalhadoSegundos,

        OffsetDateTime calculadoEm,
        @Schema(example = "3000") int metaAguaMl,

        @Schema(
                description = "Duração dos blocos de exercício escolhida ao iniciar o dia. O bloco que"
                        + " compensa um adiamento tem 10 min.",
                example = "5")
        int duracaoBlocoMin,

        @Schema(description = "Soma dos marcos de hidratação concluídos", example = "562.5")
        BigDecimal aguaIngeridaMl,

        @Schema(description = "Água e exercício em ordem de horário; na hora cheia, a água vem antes")
        List<MarcoResposta> marcos) {

    static JornadaResposta de(SituacaoDaJornada jornada) {
        return new JornadaResposta(
                jornada.id(),
                jornada.dataReferencia(),
                jornada.status(),
                jornada.iniciadaEm(),
                jornada.finalizadaEm(),
                jornada.pausadaDesde(),
                jornada.tempoTrabalhadoSegundos(),
                jornada.calculadoEm(),
                jornada.metaAguaMl(),
                jornada.duracaoBlocoMin(),
                jornada.aguaIngeridaMl(),
                jornada.marcos().stream().map(MarcoResposta::de).toList());
    }
}
