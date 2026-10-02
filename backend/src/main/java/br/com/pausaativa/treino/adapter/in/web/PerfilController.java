package br.com.pausaativa.treino.adapter.in.web;

import br.com.pausaativa.treino.application.port.in.ConsultarPerfil;
import br.com.pausaativa.treino.application.port.in.SalvarPerfil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/perfil", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Perfil", description = "Perfil físico usado para montar os blocos de exercício")
class PerfilController {

    private final ConsultarPerfil consultarPerfil;
    private final SalvarPerfil salvarPerfil;

    PerfilController(ConsultarPerfil consultarPerfil, SalvarPerfil salvarPerfil) {
        this.consultarPerfil = consultarPerfil;
        this.salvarPerfil = salvarPerfil;
    }

    @GetMapping
    @Operation(summary = "Perfil físico atual")
    @ApiResponse(responseCode = "200", description = "Perfil preenchido")
    @ApiResponse(responseCode = "204", description = "Perfil ainda não preenchido", content = @Content)
    ResponseEntity<PerfilResposta> consultar() {
        return consultarPerfil
                .consultar()
                .map(PerfilResposta::de)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cria ou substitui o perfil físico. Vale para os próximos blocos.")
    PerfilResposta salvar(@RequestBody PerfilRequisicao requisicao) {
        return PerfilResposta.de(salvarPerfil.salvar(requisicao.paraDominio()));
    }
}
