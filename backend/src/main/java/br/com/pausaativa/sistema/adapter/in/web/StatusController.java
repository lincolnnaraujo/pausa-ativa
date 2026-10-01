package br.com.pausaativa.sistema.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/sistema", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Sistema", description = "Informações sobre a própria aplicação")
class StatusController {

    static final String VERSAO_DESCONHECIDA = "desconhecida";

    private final Clock clock;
    private final String versao;

    /** O build-info é gerado pelo Maven; numa execução fora dele a versão aparece como desconhecida. */
    StatusController(Clock clock, ObjectProvider<BuildProperties> buildProperties) {
        this.clock = clock;
        BuildProperties build = buildProperties.getIfAvailable();
        this.versao = build != null ? build.getVersion() : VERSAO_DESCONHECIDA;
    }

    @GetMapping("/status")
    @Operation(summary = "Informa que o backend está no ar, com a versão e o horário do servidor")
    StatusResposta status() {
        return new StatusResposta(
                "pausa-ativa",
                versao,
                OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS),
                clock.getZone().getId());
    }
}
