package br.com.pausaativa.agenda.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Tag(name = "Eventos", description = "Notificações do servidor para a tela, em tempo real")
class EventosController {

    private final CanalDeEventos canal;

    EventosController(CanalDeEventos canal) {
        this.canal = canal;
    }

    @GetMapping(path = "/api/v1/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Stream de eventos (Server-Sent Events)",
            description = "Eventos: marco-disparado (dados no formato Marco) e jornada-atualizada (formato"
                    + " Jornada). Um comentário \"ping\" a cada 20 s mantém a conexão. Ao reconectar, busque"
                    + " GET /api/v1/jornadas/atual para recuperar os marcos pendentes.")
    @ApiResponse(
            responseCode = "200",
            description = "Conexão aberta",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE, schema = @Schema(type = "string")))
    SseEmitter conectar() {
        return canal.conectar();
    }
}
