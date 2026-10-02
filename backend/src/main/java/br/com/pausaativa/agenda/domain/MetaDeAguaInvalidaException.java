package br.com.pausaativa.agenda.domain;

public class MetaDeAguaInvalidaException extends RegraDeNegocioException {

    MetaDeAguaInvalidaException(int mililitros) {
        super("A meta de água precisa ficar entre %d e %d ml; foi informado %d ml."
                .formatted(MetaDeAgua.MINIMO, MetaDeAgua.MAXIMO, mililitros));
    }
}
