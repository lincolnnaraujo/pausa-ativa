package br.com.pausaativa.agenda.adapter.in.web;

import br.com.pausaativa.agenda.application.port.in.ConsultarJornadaAtual;
import br.com.pausaativa.agenda.application.port.in.FinalizarJornada;
import br.com.pausaativa.agenda.application.port.in.IniciarJornada;
import br.com.pausaativa.agenda.application.port.in.PausarJornada;
import br.com.pausaativa.agenda.application.port.in.RetomarJornada;
import br.com.pausaativa.agenda.domain.MetaDeAgua;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Os comandos usam o id da jornada no caminho para a repetição ser idempotente (spec H2, seção 6). */
@RestController
@RequestMapping(path = "/api/v1/jornadas", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Jornada", description = "Iniciar, pausar, retomar e finalizar o dia")
class JornadaController {

    private final ConsultarJornadaAtual consultarJornadaAtual;
    private final IniciarJornada iniciarJornada;
    private final PausarJornada pausarJornada;
    private final RetomarJornada retomarJornada;
    private final FinalizarJornada finalizarJornada;

    JornadaController(
            ConsultarJornadaAtual consultarJornadaAtual,
            IniciarJornada iniciarJornada,
            PausarJornada pausarJornada,
            RetomarJornada retomarJornada,
            FinalizarJornada finalizarJornada) {
        this.consultarJornadaAtual = consultarJornadaAtual;
        this.iniciarJornada = iniciarJornada;
        this.pausarJornada = pausarJornada;
        this.retomarJornada = retomarJornada;
        this.finalizarJornada = finalizarJornada;
    }

    @GetMapping("/atual")
    @Operation(summary = "Jornada aberta ou, sem ela, a de hoje")
    @ApiResponse(responseCode = "200", description = "Há jornada aberta ou de hoje")
    @ApiResponse(responseCode = "204", description = "Nenhuma jornada hoje", content = @Content)
    ResponseEntity<JornadaResposta> atual() {
        return consultarJornadaAtual
                .consultar()
                .map(JornadaResposta::de)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Inicia o dia. Encerra antes a jornada esquecida de um dia anterior, se houver.")
    JornadaResposta iniciar(@RequestBody(required = false) IniciarJornadaRequisicao requisicao) {
        int meta = Optional.ofNullable(requisicao)
                .map(IniciarJornadaRequisicao::metaAguaMl)
                .orElse(MetaDeAgua.PADRAO.mililitros());
        return JornadaResposta.de(iniciarJornada.iniciar(meta));
    }

    @PostMapping("/{id}/pausa")
    @Operation(summary = "Pausa a jornada (almoço). O tempo trabalhado congela.")
    JornadaResposta pausar(@PathVariable UUID id) {
        return JornadaResposta.de(pausarJornada.pausar(id));
    }

    @PostMapping("/{id}/retomada")
    @Operation(summary = "Retoma a jornada pausada")
    JornadaResposta retomar(@PathVariable UUID id) {
        return JornadaResposta.de(retomarJornada.retomar(id));
    }

    @PostMapping("/{id}/finalizacao")
    @Operation(summary = "Finaliza o dia. Repetir devolve a mesma situação.")
    JornadaResposta finalizar(@PathVariable UUID id) {
        return JornadaResposta.de(finalizarJornada.finalizar(id));
    }
}
