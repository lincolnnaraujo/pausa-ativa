package br.com.pausaativa.historico.adapter.in.web;

import br.com.pausaativa.historico.domain.DataFuturaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = HistoricoController.class)
class TratamentoDeErrosDoHistorico {

    @ExceptionHandler(DataFuturaException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail dataFutura(DataFuturaException erro) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, erro.getMessage());
        problema.setTitle("Requisição inválida");
        return problema;
    }
}
