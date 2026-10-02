package br.com.pausaativa.agenda.adapter.in.web;

import br.com.pausaativa.agenda.application.port.in.ConfirmarRecebimento;
import br.com.pausaativa.agenda.application.port.in.ResponderMarco;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/marcos", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Marco", description = "Respostas aos lembretes")
class MarcoController {

    private final ResponderMarco responderMarco;
    private final ConfirmarRecebimento confirmarRecebimento;

    MarcoController(ResponderMarco responderMarco, ConfirmarRecebimento confirmarRecebimento) {
        this.responderMarco = responderMarco;
        this.confirmarRecebimento = confirmarRecebimento;
    }

    @PostMapping("/{id}/conclusao")
    @Operation(summary = "Conclui o marco. Repetir devolve a mesma situação.")
    JornadaResposta concluir(@PathVariable UUID id) {
        return JornadaResposta.de(responderMarco.concluir(id));
    }

    @PostMapping("/{id}/falha")
    @Operation(summary = "Marca falha no marco. Repetir devolve a mesma situação.")
    JornadaResposta falhar(@PathVariable UUID id) {
        return JornadaResposta.de(responderMarco.falhar(id));
    }

    @PostMapping("/{id}/recebimento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Confirma que o marco chegou à tela",
            description = "Sem a confirmação, o prazo vencido vira NAO_ENTREGUE em vez de FALHA.")
    void confirmarRecebimento(@PathVariable UUID id) {
        confirmarRecebimento.confirmar(id);
    }
}
