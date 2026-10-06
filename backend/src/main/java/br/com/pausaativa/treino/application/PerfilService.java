package br.com.pausaativa.treino.application;

import br.com.pausaativa.treino.application.port.in.ConsultarPerfil;
import br.com.pausaativa.treino.application.port.in.SalvarPerfil;
import br.com.pausaativa.treino.application.port.out.PerfilRepository;
import br.com.pausaativa.treino.domain.PerfilFisico;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PerfilService implements ConsultarPerfil, SalvarPerfil {

    private final PerfilRepository repositorio;
    private final Clock clock;

    PerfilService(PerfilRepository repositorio, Clock clock) {
        this.repositorio = repositorio;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PerfilFisico> consultar() {
        return repositorio.buscar();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean preenchido() {
        return repositorio.buscar().isPresent();
    }

    @Override
    @Transactional
    public PerfilFisico salvar(PerfilFisico perfil) {
        repositorio.salvar(perfil, clock.instant());
        return perfil;
    }
}
