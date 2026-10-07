package br.com.pausaativa.agenda.application;

import br.com.pausaativa.agenda.application.port.in.CriarHistoricoDeExemplo;
import br.com.pausaativa.agenda.application.port.out.JornadaRepository;
import br.com.pausaativa.agenda.application.port.out.MontadorDeBlocos;
import br.com.pausaativa.agenda.domain.Jornada;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava o histórico de exemplo sem publicar nada: são dias passados, que não mudam a tela de hoje e não
 * entram nas métricas de uso.
 */
@Service
class HistoricoDeExemploService implements CriarHistoricoDeExemplo {

    private static final Logger log = LoggerFactory.getLogger(HistoricoDeExemploService.class);

    private final JornadaRepository repositorio;
    private final MontadorDeBlocos montador;
    private final Clock clock;

    HistoricoDeExemploService(JornadaRepository repositorio, MontadorDeBlocos montador, Clock clock) {
        this.repositorio = repositorio;
        this.montador = montador;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int criar() {
        Instant agora = clock.instant();
        LocalDate hoje = LocalDate.ofInstant(agora, clock.getZone());
        if (repositorio.existeAntesDe(hoje)) {
            log.info("Histórico de exemplo não criado: o banco já tem jornadas de dias anteriores");
            return 0;
        }
        List<Jornada> jornadas = new HistoricoDeExemplo(clock.getZone(), montador::montarDeExemplo).ate(agora);
        jornadas.forEach(repositorio::salvar);
        log.atInfo()
                .addKeyValue("jornadas", jornadas.size())
                .addKeyValue("de", hoje.minusDays(HistoricoDeExemplo.DIAS))
                .addKeyValue("ate", hoje.minusDays(1))
                .log("Histórico de exemplo criado");
        return jornadas.size();
    }
}
