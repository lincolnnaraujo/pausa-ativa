package br.com.pausaativa.agenda.adapter.in.agendador;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.pausaativa.agenda.application.port.in.AvancarAgenda;
import br.com.pausaativa.agenda.application.port.in.ReconciliarJornadas;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.scheduling.TaskScheduler;

class AgendadorDaAgendaTest {

    private final ReconciliarJornadas reconciliarJornadas = mock(ReconciliarJornadas.class);
    private final AvancarAgenda avancarAgenda = mock(AvancarAgenda.class);
    private final TaskScheduler tarefas = mock(TaskScheduler.class);

    private AgendadorDaAgenda agendador(ObjectProvider<TaskScheduler> provedor) {
        return new AgendadorDaAgenda(reconciliarJornadas, avancarAgenda, provedor);
    }

    private ObjectProvider<TaskScheduler> comAgendamento() {
        return new StaticListableBeanFactory(Map.of("tarefas", tarefas)).getBeanProvider(TaskScheduler.class);
    }

    @Test
    void reconciliaAntesDeLigarOTickDeUmSegundoEUmaVezSo() {
        AgendadorDaAgenda agendador = agendador(comAgendamento());

        agendador.aoSubir();
        agendador.aoSubir();

        InOrder ordem = inOrder(reconciliarJornadas, tarefas);
        ordem.verify(reconciliarJornadas).reconciliar();
        ordem.verify(tarefas).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(1)));
        verify(tarefas, times(1)).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
    }

    @Test
    void comOAgendamentoDesligadoSoReconcilia() {
        AgendadorDaAgenda agendador = agendador(new StaticListableBeanFactory().getBeanProvider(TaskScheduler.class));

        agendador.aoSubir();

        verify(reconciliarJornadas).reconciliar();
        verifyNoInteractions(tarefas, avancarAgenda);
    }

    @Test
    void falhaNumTickNaoInterrompeOsSeguintes() {
        doThrow(new IllegalStateException("banco fora do ar"))
                .when(avancarAgenda)
                .avancar();
        AgendadorDaAgenda agendador = agendador(comAgendamento());

        assertThatCode(agendador::tick).doesNotThrowAnyException();
        verify(avancarAgenda).avancar();
    }
}
