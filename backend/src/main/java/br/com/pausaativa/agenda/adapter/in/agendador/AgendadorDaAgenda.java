package br.com.pausaativa.agenda.adapter.in.agendador;

import br.com.pausaativa.agenda.application.port.in.AvancarAgenda;
import br.com.pausaativa.agenda.application.port.in.ReconciliarJornadas;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Na subida do backend: primeiro reconcilia a jornada aberta (Cenário 8), depois liga o tick de 1 s.
 *
 * <p>A ordem importa. Com um {@code @Scheduled} comum, o tick poderia rodar antes da reconciliação e
 * disparar atrasados, numa rajada, os marcos que venceram com o backend fora do ar; eles devem virar
 * {@code NAO_ENTREGUE}.
 */
@Component
class AgendadorDaAgenda {

    static final Duration INTERVALO_DO_TICK = Duration.ofSeconds(1);

    private static final Logger log = LoggerFactory.getLogger(AgendadorDaAgenda.class);

    private final ReconciliarJornadas reconciliarJornadas;
    private final AvancarAgenda avancarAgenda;
    private final ObjectProvider<TaskScheduler> agendador;
    private final AtomicBoolean tickLigado = new AtomicBoolean();

    /** O {@link TaskScheduler} só existe com o agendamento habilitado (ver {@link ConfiguracaoDoAgendador}). */
    AgendadorDaAgenda(
            ReconciliarJornadas reconciliarJornadas,
            AvancarAgenda avancarAgenda,
            ObjectProvider<TaskScheduler> agendador) {
        this.reconciliarJornadas = reconciliarJornadas;
        this.avancarAgenda = avancarAgenda;
        this.agendador = agendador;
    }

    @EventListener(ApplicationReadyEvent.class)
    void aoSubir() {
        reconciliarJornadas.reconciliar();
        agendador.ifAvailable(tarefas -> {
            if (tickLigado.compareAndSet(false, true)) {
                tarefas.scheduleWithFixedDelay(this::tick, INTERVALO_DO_TICK);
            }
        });
    }

    /** Uma falha num tick (banco fora, por exemplo) não pode parar os seguintes. */
    void tick() {
        try {
            avancarAgenda.avancar();
        } catch (RuntimeException erro) {
            log.warn("Falha ao avançar a agenda; nova tentativa no próximo tick", erro);
        }
    }
}
