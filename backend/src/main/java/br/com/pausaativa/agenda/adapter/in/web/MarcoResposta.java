package br.com.pausaativa.agenda.adapter.in.web;

import br.com.pausaativa.agenda.application.port.in.SituacaoDoMarco;
import br.com.pausaativa.agenda.domain.Categoria;
import br.com.pausaativa.agenda.domain.StatusMarco;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(
        name = "Marco",
        description = "Lembrete da jornada. Horários no fuso America/Sao_Paulo.",
        requiredProperties = {
            "id",
            "categoria",
            "sequencia",
            "status",
            "volumeMl",
            "volumeAproximadoMl",
            "segundosTrabalhadosPrevistos",
            "disparadoEm",
            "recebidoEm",
            "respondidoEm",
            "editadoEm",
            "mensagem",
            "podeAdiar",
            "podeCorrigir",
            "bloco"
        })
record MarcoResposta(
        UUID id,
        Categoria categoria,

        @Schema(description = "Ordem do marco na categoria, a partir de 1", example = "1")
        int sequencia,

        StatusMarco status,

        @Schema(
                description = "Volume exato: meta ÷ 16. Nulo no exercício.",
                types = {"number", "null"},
                example = "187.5")
        BigDecimal volumeMl,

        @Schema(
                description = "Volume para exibir, arredondado para a dezena. Nulo no exercício.",
                types = {"integer", "null"},
                format = "int32",
                example = "190")
        Integer volumeAproximadoMl,

        @Schema(description = "Tempo trabalhado em que o marco dispara", example = "1800")
        long segundosTrabalhadosPrevistos,

        @Schema(
                types = {"string", "null"},
                format = "date-time")
        OffsetDateTime disparadoEm,

        @Schema(
                types = {"string", "null"},
                format = "date-time")
        OffsetDateTime recebidoEm,

        @Schema(
                types = {"string", "null"},
                format = "date-time")
        OffsetDateTime respondidoEm,

        @Schema(
                description = "Instante da última correção. Nulo se o lembrete nunca foi corrigido.",
                types = {"string", "null"},
                format = "date-time")
        OffsetDateTime editadoEm,

        @Schema(example = "Beba ~190 ml. Levante-se para buscar a água.")
        String mensagem,

        @Schema(
                description = "Se o botão Adiar vale agora: só no exercício pendente que não compensa um"
                        + " adiamento e não é o último do dia")
        boolean podeAdiar,

        @Schema(
                description = "Se o botão Corrigir vale agora: só no lembrete concluído ou com falha, até o fim do"
                        + " dia em que a jornada começou")
        boolean podeCorrigir,

        @Schema(
                description = "Bloco de exercício. Nulo na água e no exercício que ainda não disparou.",
                types = {"object", "null"})
        BlocoResposta bloco) {

    static MarcoResposta de(SituacaoDoMarco marco) {
        return new MarcoResposta(
                marco.id(),
                marco.categoria(),
                marco.sequencia(),
                marco.status(),
                marco.volumeMl(),
                marco.volumeAproximadoMl(),
                marco.segundosTrabalhadosPrevistos(),
                marco.disparadoEm(),
                marco.recebidoEm(),
                marco.respondidoEm(),
                marco.editadoEm(),
                marco.mensagem(),
                marco.podeAdiar(),
                marco.podeCorrigir(),
                marco.bloco() == null ? null : BlocoResposta.de(marco.bloco()));
    }
}
