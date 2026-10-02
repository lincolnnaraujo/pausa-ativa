package br.com.pausaativa.agenda.application;

import br.com.pausaativa.agenda.application.port.in.ReconciliarJornadas;
import br.com.pausaativa.agenda.application.port.out.JornadaRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReconciliacaoService implements ReconciliarJornadas {

    private static final Logger log = LoggerFactory.getLogger(ReconciliacaoService.class);

    private final JornadaRepository repositorio;
    private final PublicadorDeAlteracoes publicador;
    private final Clock clock;

    ReconciliacaoService(JornadaRepository repositorio, PublicadorDeAlteracoes publicador, Clock clock) {
        this.repositorio = repositorio;
        this.publicador = publicador;
        this.clock = clock;
    }

    /** Primeiro acerta o que venceu com o backend fora; depois vê se a jornada ficou esquecida. */
    @Override
    @Transactional
    public void reconciliar() {
        Instant agora = clock.instant();
        repositorio.buscarAbertaComBloqueio().ifPresent(jornada -> {
            boolean mudou = jornada.reconciliar(agora);
            if (jornada.esquecida(agora, clock.getZone())) {
                mudou = jornada.encerrarAutomaticamente(agora);
                log.info("Jornada {} de {} encerrada automaticamente", jornada.id(), jornada.dataReferencia());
            }
            if (mudou) {
                repositorio.salvar(jornada);
                publicador.publicar(jornada, agora);
            }
        });
    }
}
