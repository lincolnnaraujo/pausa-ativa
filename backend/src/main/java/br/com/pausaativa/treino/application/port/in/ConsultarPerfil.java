package br.com.pausaativa.treino.application.port.in;

import br.com.pausaativa.treino.domain.PerfilFisico;
import java.util.Optional;

public interface ConsultarPerfil {

    /** Vazio enquanto o usuário não preencheu o perfil. */
    Optional<PerfilFisico> consultar();

    /** Sem perfil, o dia não começa (Cenário 6 da H3). É o que a Agenda consulta. */
    boolean preenchido();
}
