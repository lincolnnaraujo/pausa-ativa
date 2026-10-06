package br.com.pausaativa.treino.adapter.out.persistence;

import br.com.pausaativa.treino.application.port.out.CatalogoRepository;
import br.com.pausaativa.treino.domain.Exercicio;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
class CatalogoRepositoryJpa implements CatalogoRepository {

    private final ExercicioJpa jpa;

    CatalogoRepositoryJpa(ExercicioJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Exercicio> todos() {
        return jpa.findAll(Sort.by("ordem")).stream()
                .map(ExercicioEntity::paraDominio)
                .toList();
    }
}
