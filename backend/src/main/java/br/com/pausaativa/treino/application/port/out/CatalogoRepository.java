package br.com.pausaativa.treino.application.port.out;

import br.com.pausaativa.treino.domain.Exercicio;
import java.util.List;

/** O catálogo de exercícios, inserido pela migração {@code V3}. Só leitura. */
public interface CatalogoRepository {

    List<Exercicio> todos();
}
