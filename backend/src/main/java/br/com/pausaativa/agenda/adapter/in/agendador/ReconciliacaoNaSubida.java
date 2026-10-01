package br.com.pausaativa.agenda.adapter.in.agendador;

import br.com.pausaativa.agenda.application.port.in.ReconciliarJornadas;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Na subida do backend, acerta a jornada aberta e encerra a esquecida (Cenário 8). */
@Component
class ReconciliacaoNaSubida {

    private final ReconciliarJornadas reconciliarJornadas;

    ReconciliacaoNaSubida(ReconciliarJornadas reconciliarJornadas) {
        this.reconciliarJornadas = reconciliarJornadas;
    }

    @EventListener(ApplicationReadyEvent.class)
    void reconciliar() {
        reconciliarJornadas.reconciliar();
    }
}
