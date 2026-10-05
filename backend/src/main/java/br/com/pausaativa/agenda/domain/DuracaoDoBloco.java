package br.com.pausaativa.agenda.domain;

import java.time.Duration;

/** Duração do bloco de exercício, escolhida ao iniciar o dia: 5 min (padrão) ou 10 min. */
public record DuracaoDoBloco(int minutos) {

    public static final DuracaoDoBloco CINCO_MINUTOS = new DuracaoDoBloco(5);
    public static final DuracaoDoBloco DEZ_MINUTOS = new DuracaoDoBloco(10);
    public static final DuracaoDoBloco PADRAO = CINCO_MINUTOS;

    public DuracaoDoBloco {
        if (minutos != 5 && minutos != 10) {
            throw new DuracaoDoBlocoInvalidaException(minutos);
        }
    }

    public Duration duracao() {
        return Duration.ofMinutes(minutos);
    }
}
