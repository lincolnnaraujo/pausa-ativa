package br.com.pausaativa.treino.adapter.in.web;

/** Requisição de perfil sem um campo obrigatório. A mensagem é exibida ao usuário. */
class PerfilInvalidoException extends RuntimeException {

    PerfilInvalidoException(String mensagem) {
        super(mensagem);
    }
}
