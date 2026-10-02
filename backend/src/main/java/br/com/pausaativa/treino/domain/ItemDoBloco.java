package br.com.pausaativa.treino.domain;

import java.time.Duration;
import java.util.Objects;

/** Um exercício do bloco, com a quantidade do nível do perfil. */
public record ItemDoBloco(Exercicio exercicio, Quantidade quantidade) {

    /** Tempo para trocar de exercício, somado a cada item (decisão D2 da spec H3). */
    public static final Duration TROCA = Duration.ofSeconds(15);

    public ItemDoBloco {
        Objects.requireNonNull(exercicio, "exercicio");
        Objects.requireNonNull(quantidade, "quantidade");
    }

    /** Execução mais a troca. */
    public Duration estimativa() {
        return quantidade.execucao().plus(TROCA);
    }
}
