package br.com.pausaativa.agenda.adapter.in.web;

/** Corpo da correção sem o status: vira 400, com a mensagem em português. */
class CorrecaoInvalidaException extends RuntimeException {

    CorrecaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
