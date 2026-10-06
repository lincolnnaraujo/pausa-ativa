package br.com.pausaativa.treino.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** O bloco montado para um marco: a duração pedida e os exercícios, na ordem em que devem ser feitos. */
public record BlocoDeExercicio(Duration duracao, List<ItemDoBloco> itens) {

    public BlocoDeExercicio {
        Objects.requireNonNull(duracao, "duracao");
        itens = List.copyOf(itens);
    }

    /** Soma das estimativas dos itens; nunca passa da duração. */
    public Duration estimativa() {
        return itens.stream().map(ItemDoBloco::estimativa).reduce(Duration.ZERO, Duration::plus);
    }
}
