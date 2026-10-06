package br.com.pausaativa.agenda.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * Exercício do bloco como foi proposto no disparo: uma cópia do catálogo do Treino, para o histórico
 * mostrar o que foi pedido mesmo que o catálogo mude depois (decisão D1 da spec H3).
 *
 * @param quantidade texto para a tela: "10 repetições", "6 por lado", "20 s"
 * @param estimativa execução mais a troca de exercício
 */
public record ExercicioProposto(
        String codigo, String nome, String grupo, String quantidade, Duration estimativa, String instrucao) {

    public ExercicioProposto {
        Objects.requireNonNull(codigo, "codigo");
        Objects.requireNonNull(nome, "nome");
        Objects.requireNonNull(estimativa, "estimativa");
    }
}
