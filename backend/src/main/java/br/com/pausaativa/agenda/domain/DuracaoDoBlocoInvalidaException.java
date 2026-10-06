package br.com.pausaativa.agenda.domain;

public class DuracaoDoBlocoInvalidaException extends RegraDeNegocioException {

    DuracaoDoBlocoInvalidaException(int minutos) {
        super("O bloco de exercício tem 5 ou 10 min; foi informado %d min.".formatted(minutos));
    }
}
