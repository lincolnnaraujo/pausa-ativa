package br.com.pausaativa.treino.application.port.in;

import br.com.pausaativa.treino.domain.PerfilFisico;

public interface SalvarPerfil {

    /** Cria ou substitui o perfil. Vale para os blocos montados daqui em diante. */
    PerfilFisico salvar(PerfilFisico perfil);
}
