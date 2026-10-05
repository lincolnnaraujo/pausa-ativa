package br.com.pausaativa.agenda.adapter.out.persistence;

import br.com.pausaativa.agenda.domain.ExercicioProposto;
import jakarta.persistence.Embeddable;
import java.time.Duration;

/** Linha de {@code item_do_bloco}: um exercício do bloco, na posição {@code ordem} (a partir de 1). */
@Embeddable
record ItemDoBlocoEmbeddable(
        int ordem,
        String exercicioCodigo,
        String nome,
        String grupo,
        String quantidadeTexto,
        int segundosEstimados,
        String instrucao) {

    static ItemDoBlocoEmbeddable de(int ordem, ExercicioProposto exercicio) {
        return new ItemDoBlocoEmbeddable(
                ordem,
                exercicio.codigo(),
                exercicio.nome(),
                exercicio.grupo(),
                exercicio.quantidade(),
                Math.toIntExact(exercicio.estimativa().toSeconds()),
                exercicio.instrucao());
    }

    ExercicioProposto paraDominio() {
        return new ExercicioProposto(
                exercicioCodigo, nome, grupo, quantidadeTexto, Duration.ofSeconds(segundosEstimados), instrucao);
    }
}
