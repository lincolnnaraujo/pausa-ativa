package br.com.pausaativa.agenda.adapter.out.metricas;

import br.com.pausaativa.agenda.application.port.in.SituacaoDoBloco;
import br.com.pausaativa.agenda.application.port.in.SituacaoDoMarco;
import br.com.pausaativa.agenda.application.port.out.JornadaAlterada;
import br.com.pausaativa.agenda.domain.EventoDaJornada;
import br.com.pausaativa.agenda.domain.MarcoAdiado;
import br.com.pausaativa.agenda.domain.MarcoCorrigido;
import br.com.pausaativa.agenda.domain.MarcoDisparado;
import br.com.pausaativa.agenda.domain.MarcoEncerrado;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Métricas e logs dos marcos (spec H2, seção 10), dos blocos de exercício (spec H3, seção 10) e das
 * correções (spec H4, seção 10). Os logs saem em JSON com {@code jornadaId} e {@code marcoId} como campos;
 * os painéis ficam para a H5.
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
                case MarcoDisparado disparado -> {
                    registrarDisparo(jornadaId, disparado);
                    blocoDo(alteracao, disparado).ifPresent(bloco -> registrarBloco(jornadaId, disparado, bloco));
                }
                case MarcoEncerrado encerrado -> registrarEncerramento(jornadaId, encerrado);
                case MarcoAdiado adiado -> registrarAdiamento(jornadaId, adiado);
                case MarcoCorrigido corrigido -> registrarCorrecao(jornadaId, corrigido);
            }
        }
    }

    /** O bloco é montado depois do disparo, na mesma alteração: vem na situação publicada. */
    private static Optional<SituacaoDoBloco> blocoDo(JornadaAlterada alteracao, MarcoDisparado disparado) {
        return alteracao.situacao().marcos().stream()
                .filter(marco -> marco.id().equals(disparado.marcoId()))
                .map(SituacaoDoMarco::bloco)
                .filter(Objects::nonNull)
                .findFirst();
    }

    private void registrarBloco(UUID jornadaId, MarcoDisparado disparado, SituacaoDoBloco bloco) {
        metricas.counter(
                        "pausaativa.blocos.montados",
                        "duracao",
                        String.valueOf(bloco.duracaoMin()),
                        "compensa_adiamento",
                        String.valueOf(bloco.compensaAdiamento()))
                .increment();
        DistributionSummary.builder("pausaativa.blocos.itens")
                .description("Quantidade de exercícios por bloco")
                .register(metricas)
                .record(bloco.exercicios().size());
        log.atInfo()
                .addKeyValue("jornadaId", jornadaId)
                .addKeyValue("marcoId", disparado.marcoId())
                .addKeyValue("sequencia", disparado.sequencia())
                .addKeyValue("duracaoMin", bloco.duracaoMin())
                .addKeyValue("compensaAdiamento", bloco.compensaAdiamento())
                .addKeyValue(
                        "exercicios",
                        bloco.exercicios().stream()
                                .map(SituacaoDoBloco.Exercicio::codigo)
                                .toList())
                .log("Bloco de exercício proposto");
    }

    private void registrarAdiamento(UUID jornadaId, MarcoAdiado adiado) {
        metricas.counter("pausaativa.marcos.adiados").increment();
        log.atInfo()
                .addKeyValue("jornadaId", jornadaId)
                .addKeyValue("marcoId", adiado.marcoId())
                .addKeyValue("sequencia", adiado.sequencia())
                .log("Bloco de exercício adiado");
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

    private void registrarCorrecao(UUID jornadaId, MarcoCorrigido corrigido) {
        String categoria = corrigido.categoria().name();
        metricas.counter(
                        "pausaativa.marcos.corrigidos",
                        "categoria",
                        categoria,
                        "para",
                        corrigido.para().name())
                .increment();
        log.atInfo()
                .addKeyValue("jornadaId", jornadaId)
                .addKeyValue("marcoId", corrigido.marcoId())
                .addKeyValue("categoria", categoria)
                .addKeyValue("sequencia", corrigido.sequencia())
                .addKeyValue("de", corrigido.de().name())
                .addKeyValue("para", corrigido.para().name())
                .log("Marco corrigido");
    }
}
