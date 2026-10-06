package br.com.pausaativa.treino.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Perfil inválido vira Problem Details (RFC 9457), com a mensagem em português no {@code detail}. */
@RestControllerAdvice(assignableTypes = PerfilController.class)
class TratamentoDeErrosDoTreino {

    @ExceptionHandler(PerfilInvalidoException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail perfilInvalido(PerfilInvalidoException erro) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, erro.getMessage());
        problema.setTitle("Requisição inválida");
        return problema;
    }
}
