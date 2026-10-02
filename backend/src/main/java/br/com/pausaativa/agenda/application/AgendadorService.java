package br.com.pausaativa.agenda.application;

import br.com.pausaativa.agenda.application.port.in.AvancarAgenda;
import br.com.pausaativa.agenda.application.port.out.JornadaRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AgendadorService implements AvancarAgenda {

    private static final Logger log = LoggerFactory.getLogger(AgendadorService.class);

    private final JornadaRepository repositorio;
    private final PublicadorDeAlteracoes publicador;
    private final Clock clock;

    AgendadorService(JornadaRepository repositorio, PublicadorDeAlteracoes publicador, Clock clock) {
        this.repositorio = repositorio;
        this.publicador = publicador;
        this.clock = clock;
    }

    /** Só grava e anuncia quando algo mudou; na maioria dos segundos, nada muda. */
    @Override
    @Transactional
    public void avancar() {
        Instant agora = clock.instant();
        repositorio.buscarAbertaComBloqueio().ifPresent(jornada -> {
            boolean mudou;
            if (jornada.esquecida(agora, clock.getZone())) {
                mudou = jornada.encerrarAutomaticamente(agora);
                log.info("Jornada {} de {} encerrada automaticamente", jornada.id(), jornada.dataReferencia());
            } else {
                mudou = jornada.avancar(agora).houveMudanca();
            }
            if (mudou) {
                repositorio.salvar(jornada);
                publicador.publicar(jornada, agora);
            }
        });
    }
}
