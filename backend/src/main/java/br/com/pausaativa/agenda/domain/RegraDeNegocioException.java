package br.com.pausaativa.agenda.domain;

/** Operação recusada por uma regra de domínio. A mensagem é exibida ao usuário. */
public abstract class RegraDeNegocioException extends RuntimeException {

    protected RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
