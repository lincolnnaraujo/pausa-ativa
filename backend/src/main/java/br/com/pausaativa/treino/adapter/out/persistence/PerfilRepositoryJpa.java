package br.com.pausaativa.treino.adapter.out.persistence;

import br.com.pausaativa.treino.application.port.out.PerfilRepository;
import br.com.pausaativa.treino.domain.PerfilFisico;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class PerfilRepositoryJpa implements PerfilRepository {

    private final PerfilFisicoJpa jpa;

    PerfilRepositoryJpa(PerfilFisicoJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<PerfilFisico> buscar() {
        return jpa.findById(PerfilFisicoEntity.ID_UNICO).map(PerfilFisicoEntity::paraDominio);
    }

    @Override
    public void salvar(PerfilFisico perfil, Instant atualizadoEm) {
        PerfilFisicoEntity entidade = jpa.findById(PerfilFisicoEntity.ID_UNICO).orElseGet(PerfilFisicoEntity::novo);
        entidade.atualizarCom(perfil, atualizadoEm);
        jpa.save(entidade);
    }
}
