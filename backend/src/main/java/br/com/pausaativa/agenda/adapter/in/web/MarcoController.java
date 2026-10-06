package br.com.pausaativa.agenda.adapter.in.web;

import br.com.pausaativa.agenda.application.port.in.ConfirmarRecebimento;
import br.com.pausaativa.agenda.application.port.in.CorrigirMarco;
import br.com.pausaativa.agenda.application.port.in.ResponderMarco;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/marcos", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Marco", description = "Respostas aos lembretes")
class MarcoController {

    private final ResponderMarco responderMarco;
    private final ConfirmarRecebimento confirmarRecebimento;
    private final CorrigirMarco corrigirMarco;

    MarcoController(
            ResponderMarco responderMarco, ConfirmarRecebimento confirmarRecebimento, CorrigirMarco corrigirMarco) {
        this.responderMarco = responderMarco;
        this.confirmarRecebimento = confirmarRecebimento;
        this.corrigirMarco = corrigirMarco;
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

    @PostMapping("/{id}/adiamento")
    @Operation(
            summary = "Adia o bloco de exercício. Repetir devolve a mesma situação.",
            description = "O bloco seguinte passa a ter 10 min e decide o destino dos dois. Só vale uma vez por"
                    + " cadeia e nunca no último bloco do dia; a água não é adiada.")
    JornadaResposta adiar(@PathVariable UUID id) {
        return JornadaResposta.de(responderMarco.adiar(id));
    }

    @PostMapping(path = "/{id}/correcao", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Corrige a resposta de um lembrete de hoje. Corrigir para a situação atual devolve a mesma.",
            description = "Concluído vira falha, ou o contrário, até o fim do dia em que a jornada começou. Num"
                    + " par adiado, corrige os dois. O lembrete fica marcado como editado.")
    JornadaResposta corrigir(@PathVariable UUID id, @RequestBody CorrecaoRequisicao requisicao) {
        return JornadaResposta.de(corrigirMarco.corrigir(id, requisicao.paraDominio()));
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
