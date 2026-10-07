package br.com.pausaativa.agenda.adapter.in.web;

import br.com.pausaativa.agenda.application.JornadaNaoEncontradaException;
import br.com.pausaativa.agenda.application.PerfilAusenteException;
import br.com.pausaativa.agenda.domain.AdiamentoRecusadoException;
import br.com.pausaativa.agenda.domain.CorrecaoRecusadaException;
import br.com.pausaativa.agenda.domain.DuracaoDoBlocoInvalidaException;
import br.com.pausaativa.agenda.domain.JornadaJaIniciadaException;
import br.com.pausaativa.agenda.domain.MarcoNaoEncontradoException;
import br.com.pausaativa.agenda.domain.MetaDeAguaInvalidaException;
import br.com.pausaativa.agenda.domain.RegraDeNegocioException;
import br.com.pausaativa.agenda.domain.RespostaDeMarcoRecusadaException;
import br.com.pausaativa.agenda.domain.TransicaoDeJornadaInvalidaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Regras de negócio recusadas viram Problem Details (RFC 9457), com a mensagem do domínio, em
 * português, no {@code detail}.
 */
@RestControllerAdvice(assignableTypes = {JornadaController.class, MarcoController.class})
class TratamentoDeErrosDaAgenda {

    @ExceptionHandler({MetaDeAguaInvalidaException.class, DuracaoDoBlocoInvalidaException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail requisicaoInvalida(RegraDeNegocioException erro) {
        return problema(HttpStatus.BAD_REQUEST, "Requisição inválida", erro);
    }

    @ExceptionHandler({JornadaNaoEncontradaException.class, MarcoNaoEncontradoException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ProblemDetail naoEncontrado(RegraDeNegocioException erro) {
        return problema(HttpStatus.NOT_FOUND, "Não encontrado", erro);
    }

    @ExceptionHandler({
        JornadaJaIniciadaException.class,
        PerfilAusenteException.class,
        TransicaoDeJornadaInvalidaException.class,
        RespostaDeMarcoRecusadaException.class,
        AdiamentoRecusadoException.class,
        CorrecaoRecusadaException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail operacaoRecusada(RegraDeNegocioException erro) {
        return problema(HttpStatus.CONFLICT, "Operação recusada", erro);
    }

    @ExceptionHandler(CorrecaoInvalidaException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail correcaoInvalida(CorrecaoInvalidaException erro) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, erro.getMessage());
        problema.setTitle("Requisição inválida");
        return problema;
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, RegraDeNegocioException erro) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, erro.getMessage());
        problema.setTitle(titulo);
        return problema;
    }
}
