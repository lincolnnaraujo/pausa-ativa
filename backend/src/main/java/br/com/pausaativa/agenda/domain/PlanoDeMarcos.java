package br.com.pausaativa.agenda.domain;

import java.time.Duration;

/**
 * Quantos marcos uma categoria tem por jornada e a cada quanto tempo trabalhado.
 *
 * <p>O intervalo é configurável para o modo demonstração (spec H2, seção 9); em uso normal é 30 min.
 */
public record PlanoDeMarcos(Categoria categoria, Duration intervalo, int quantidade) {

    public static final Duration INTERVALO_PADRAO = Duration.ofMinutes(30);
    public static final int MARCOS_DE_HIDRATACAO = 16;
    public static final int MARCOS_DE_EXERCICIO = 8;

    public PlanoDeMarcos {
        if (intervalo.isNegative() || intervalo.isZero()) {
            throw new IllegalArgumentException("O intervalo entre marcos precisa ser positivo: " + intervalo);
        }
        if (quantidade < 1) {
            throw new IllegalArgumentException("A quantidade de marcos precisa ser positiva: " + quantidade);
        }
    }

    public static PlanoDeMarcos hidratacao(Duration intervalo) {
        return new PlanoDeMarcos(Categoria.HIDRATACAO, intervalo, MARCOS_DE_HIDRATACAO);
    }

    /**
     * O exercício dispara a cada dois intervalos da água (decisão D9 da spec H3): 60 min em uso normal,
     * sempre na mesma hora cheia que um marco de água par.
     */
    public static PlanoDeMarcos exercicio(Duration intervaloDaAgua) {
        return new PlanoDeMarcos(Categoria.EXERCICIO, intervaloDaAgua.multipliedBy(2), MARCOS_DE_EXERCICIO);
    }
}
