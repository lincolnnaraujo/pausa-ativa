package br.com.pausaativa.historico.adapter.in.web;

import br.com.pausaativa.historico.application.port.in.ConsultarHistorico;
import br.com.pausaativa.historico.domain.TipoDePeriodo;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/historico", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Histórico", description = "Situações e taxa de sucesso por dia, semana e mês")
class HistoricoController {

    private final ConsultarHistorico consultarHistorico;
    private final MeterRegistry metricas;

    HistoricoController(ConsultarHistorico consultarHistorico, MeterRegistry metricas) {
        this.consultarHistorico = consultarHistorico;
        this.metricas = metricas;
    }

    /** O timer mede o requisito de 500 ms da agregação mensal (spec H4, seção 10). */
    @GetMapping
    @Operation(
            summary = "Resumo do dia, da semana (segunda a domingo) ou do mês que contém a data",
            description = "Taxa = concluídos ÷ (concluídos + falhas), por categoria. A data não pode ser futura.")
    HistoricoResposta consultar(
            @RequestParam TipoDePeriodo periodo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        Timer.Sample amostra = Timer.start(metricas);
        HistoricoResposta resposta = HistoricoResposta.de(consultarHistorico.consultar(periodo, data));
        amostra.stop(Timer.builder("pausaativa.historico.consultas")
                .description("Tempo para montar uma visão do histórico")
                .tag("periodo", periodo.name())
                .register(metricas));
        return resposta;
    }
}
