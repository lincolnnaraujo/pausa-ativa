package br.com.pausaativa.historico.domain;

import java.time.LocalDate;

/** O histórico vai até hoje: um período não pode ser pedido a partir de uma data futura (spec H4, seção 3.2). */
public class DataFuturaException extends RuntimeException {

    public DataFuturaException(LocalDate data) {
        super("O histórico vai até hoje: %s ainda não chegou.".formatted(data));
    }
}
