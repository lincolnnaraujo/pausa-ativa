package br.com.pausaativa.agenda.application;

import br.com.pausaativa.agenda.application.port.in.SituacaoDaJornada;
import br.com.pausaativa.agenda.application.port.out.JornadaAlterada;
import br.com.pausaativa.agenda.domain.Jornada;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Anuncia a jornada gravada com os eventos acumulados no agregado. Usado por todos os casos de uso. */
@Component
class PublicadorDeAlteracoes {

    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    PublicadorDeAlteracoes(ApplicationEventPublisher eventos, Clock clock) {
        this.eventos = eventos;
        this.clock = clock;
    }

    SituacaoDaJornada publicar(Jornada jornada, Instant agora) {
        SituacaoDaJornada situacao = SituacaoDaJornada.de(jornada, agora, clock.getZone());
        eventos.publishEvent(new JornadaAlterada(situacao, jornada.extrairEventos()));
        return situacao;
    }
}
