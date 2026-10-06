package br.com.pausaativa.agenda.application;

import br.com.pausaativa.agenda.domain.RegraDeNegocioException;

/** Cenário 6 da H3: sem perfil físico, o dia não começa. O backend recusa mesmo sem passar pela tela (D7). */
public class PerfilAusenteException extends RegraDeNegocioException {

    PerfilAusenteException() {
        super("Preencha o perfil físico antes de iniciar o dia: os blocos de exercício são montados com ele.");
    }
}
