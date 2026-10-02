package br.com.pausaativa.agenda.adapter.out.metricas;

import br.com.pausaativa.agenda.application.port.out.JornadaAlterada;
import br.com.pausaativa.agenda.domain.EventoDaJornada;
import br.com.pausaativa.agenda.domain.MarcoDisparado;
import br.com.pausaativa.agenda.domain.MarcoEncerrado;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Métricas e logs dos marcos (spec H2, seção 10). Os logs saem em JSON com {@code jornadaId} e
 * {@code marcoId} como campos; os painéis ficam para a H5.
 */
@Component
class ObservabilidadeDaAgenda {

    private static final Logger log = LoggerFactory.getLogger(ObservabilidadeDaAgenda.class);

    private final MeterRegistry metricas;

    ObservabilidadeDaAgenda(MeterRegistry metricas) {
        this.metricas = metricas;
    }

    @TransactionalEventListener(fallbackExecution = true)
    void aoAlterarJornada(JornadaAlterada alteracao) {
        UUID jornadaId = alteracao.situacao().id();
        for (EventoDaJornada evento : alteracao.eventos()) {
            switch (evento) {
                case MarcoDisparado disparado -> registrarDisparo(jornadaId, disparado);
                case MarcoEncerrado encerrado -> registrarEncerramento(jornadaId, encerrado);
            }
        }
    }

    private void registrarDisparo(UUID jornadaId, MarcoDisparado disparado) {
        String categoria = disparado.categoria().name();
        metricas.counter("pausaativa.marcos.disparados", "categoria", categoria).increment();
        Timer.builder("pausaativa.marcos.atraso.disparo")
                .description("Atraso entre o instante calculado do marco e o disparo")
                .tag("categoria", categoria)
                .register(metricas)
                .record(disparado.atraso());
        log.atInfo()
                .addKeyValue("jornadaId", jornadaId)
                .addKeyValue("marcoId", disparado.marcoId())
                .addKeyValue("categoria", categoria)
                .addKeyValue("sequencia", disparado.sequencia())
                .addKeyValue("atrasoMs", disparado.atraso().toMillis())
                .log("Marco disparado");
    }

    private void registrarEncerramento(UUID jornadaId, MarcoEncerrado encerrado) {
        String categoria = encerrado.categoria().name();
        String status = encerrado.status().name();
        metricas.counter("pausaativa.marcos.encerrados", "categoria", categoria, "status", status)
                .increment();
        log.atInfo()
                .addKeyValue("jornadaId", jornadaId)
                .addKeyValue("marcoId", encerrado.marcoId())
                .addKeyValue("categoria", categoria)
                .addKeyValue("sequencia", encerrado.sequencia())
                .addKeyValue("status", status)
                .log("Marco encerrado");
    }
}
