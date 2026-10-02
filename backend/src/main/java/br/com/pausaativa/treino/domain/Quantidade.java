package br.com.pausaativa.treino.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * Quanto fazer de um exercício e quanto tempo isso leva. A estimativa (decisão D2 da spec H3) vale 3 s
 * por repetição e 1 s por segundo; "por lado" dobra.
 */
public record Quantidade(FormaDeQuantidade forma, int valor) {

    static final Duration POR_REPETICAO = Duration.ofSeconds(3);

    public Quantidade {
        Objects.requireNonNull(forma, "forma");
        if (valor < 1) {
            throw new IllegalArgumentException("A quantidade precisa ser positiva: " + valor);
        }
    }

    /** Tempo de execução, sem a troca de exercício. */
    public Duration execucao() {
        return switch (forma) {
            case REPETICOES -> POR_REPETICAO.multipliedBy(valor);
            case POR_LADO -> POR_REPETICAO.multipliedBy(valor * 2L);
            case SEGUNDOS -> Duration.ofSeconds(valor);
            case SEGUNDOS_POR_LADO -> Duration.ofSeconds(valor * 2L);
        };
    }

    /** Texto para a tela: "10 repetições", "6 por lado", "20 s", "30 s por lado". */
    public String texto() {
        return switch (forma) {
            case REPETICOES -> valor == 1 ? "1 repetição" : valor + " repetições";
            case POR_LADO -> valor + " por lado";
            case SEGUNDOS -> valor + " s";
            case SEGUNDOS_POR_LADO -> valor + " s por lado";
        };
    }
}
