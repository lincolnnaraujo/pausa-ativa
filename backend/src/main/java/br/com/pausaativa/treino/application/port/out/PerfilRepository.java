package br.com.pausaativa.treino.application.port.out;

import br.com.pausaativa.treino.domain.PerfilFisico;
import java.time.Instant;
import java.util.Optional;

/** O perfil único do usuário. */
public interface PerfilRepository {

    Optional<PerfilFisico> buscar();

    void salvar(PerfilFisico perfil, Instant atualizadoEm);
}
