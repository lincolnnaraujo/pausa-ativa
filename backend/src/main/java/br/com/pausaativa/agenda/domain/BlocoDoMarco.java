package br.com.pausaativa.agenda.domain;

import java.time.Duration;
import java.util.List;

/**
 * Bloco de um marco de exercício. Nasce no disparo, com a duração e sem exercícios; a aplicação pede
 * os exercícios ao Treino e os atribui na mesma transação (spec H3, seção 4).
 *
 * @param compensaAdiamento se o bloco anterior foi adiado e este o compensa
 */
public record BlocoDoMarco(DuracaoDoBloco duracao, boolean compensaAdiamento, List<ExercicioProposto> exercicios) {

    public BlocoDoMarco {
        exercicios = List.copyOf(exercicios);
    }

    static BlocoDoMarco semExercicios(DuracaoDoBloco duracao, boolean compensaAdiamento) {
        return new BlocoDoMarco(duracao, compensaAdiamento, List.of());
    }

    /** Soma das estimativas dos exercícios. */
    public Duration estimativa() {
        return exercicios.stream().map(ExercicioProposto::estimativa).reduce(Duration.ZERO, Duration::plus);
    }

    BlocoDoMarco comExercicios(List<ExercicioProposto> propostos) {
        if (!exercicios.isEmpty()) {
            throw new IllegalStateException("O bloco já tem exercícios");
        }
        if (propostos.isEmpty()) {
            throw new IllegalArgumentException("O bloco precisa de pelo menos um exercício");
        }
        BlocoDoMarco preenchido = new BlocoDoMarco(duracao, compensaAdiamento, propostos);
        if (preenchido.estimativa().compareTo(duracao.duracao()) > 0) {
            throw new IllegalArgumentException("Os exercícios (%s) não cabem no bloco de %d min"
                    .formatted(preenchido.estimativa(), duracao.minutos()));
        }
        return preenchido;
    }
}
