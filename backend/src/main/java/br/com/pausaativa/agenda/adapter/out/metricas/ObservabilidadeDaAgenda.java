package br.com.pausaativa.agenda.adapter.out.metricas;

import br.com.pausaativa.agenda.application.port.in.SituacaoDoBloco;
import br.com.pausaativa.agenda.application.port.in.SituacaoDoMarco;
import br.com.pausaativa.agenda.application.port.out.JornadaAlterada;
import br.com.pausaativa.agenda.domain.Categoria;
import br.com.pausaativa.agenda.domain.DuracaoDoBloco;
import br.com.pausaativa.agenda.domain.EventoDaJornada;
import br.com.pausaativa.agenda.domain.MarcoAdiado;
import br.com.pausaativa.agenda.domain.MarcoCorrigido;
import br.com.pausaativa.agenda.domain.MarcoDisparado;
import br.com.pausaativa.agenda.domain.MarcoEncerrado;
import br.com.pausaativa.agenda.domain.StatusMarco;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
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
 * o painel do Grafana lê as métricas (spec H5, seção 3.2).
 */
@Component
class ObservabilidadeDaAgenda {

    private static final Logger log = LoggerFactory.getLogger(ObservabilidadeDaAgenda.class);

    /** As durações de bloco que existem: 5 ou 10 min, e só 10 quando compensa um adiado (D3 da H3). */
    private static final List<Map.Entry<Integer, Boolean>> BLOCOS = List.of(
            Map.entry(DuracaoDoBloco.CINCO_MINUTOS.minutos(), false),
            Map.entry(DuracaoDoBloco.DEZ_MINUTOS.minutos(), false),
            Map.entry(DuracaoDoBloco.DEZ_MINUTOS.minutos(), true));

    private final MeterRegistry metricas;

    ObservabilidadeDaAgenda(MeterRegistry metricas) {
        this.metricas = metricas;
        registrarZeradas();
    }

    /**
     * Cria as séries zeradas na subida (spec H5, seção 3.2). O Micrometer só cria um medidor no primeiro
     * evento, e a série já nasce valendo 1: o {@code increase()} do Prometheus, que mede a diferença entre
     * amostras, perderia esse primeiro evento no painel.
     */
    private void registrarZeradas() {
        metricas.counter("pausaativa.marcos.adiados");
        for (Categoria categoria : Categoria.values()) {
            disparados(categoria);
            atrasoDoDisparo(categoria);
            for (StatusMarco status : StatusMarco.values()) {
                if (status.encerrado()) {
                    encerrados(categoria, status);
                }
            }
            corrigidos(categoria, StatusMarco.CONCLUIDO);
            corrigidos(categoria, StatusMarco.FALHA);
        }
        BLOCOS.forEach(bloco -> blocosMontados(bloco.getKey(), bloco.getValue()));
        itensPorBloco();
    }

    private Counter disparados(Categoria categoria) {
        return metricas.counter("pausaativa.marcos.disparados", "categoria", categoria.name());
    }

    private Timer atrasoDoDisparo(Categoria categoria) {
        return Timer.builder("pausaativa.marcos.atraso.disparo")
                .description("Atraso entre o instante calculado do marco e o disparo")
                .tag("categoria", categoria.name())
                .register(metricas);
    }

    private Counter encerrados(Categoria categoria, StatusMarco status) {
        return metricas.counter("pausaativa.marcos.encerrados", "categoria", categoria.name(), "status", status.name());
    }

    private Counter corrigidos(Categoria categoria, StatusMarco para) {
        return metricas.counter("pausaativa.marcos.corrigidos", "categoria", categoria.name(), "para", para.name());
    }

    private Counter blocosMontados(int duracaoMin, boolean compensaAdiamento) {
        return metricas.counter(
                "pausaativa.blocos.montados",
                "duracao",
                String.valueOf(duracaoMin),
                "compensa_adiamento",
                String.valueOf(compensaAdiamento));
    }

    private DistributionSummary itensPorBloco() {
        return DistributionSummary.builder("pausaativa.blocos.itens")
                .description("Quantidade de exercícios por bloco")
                .register(metricas);
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
        blocosMontados(bloco.duracaoMin(), bloco.compensaAdiamento()).increment();
        itensPorBloco().record(bloco.exercicios().size());
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
        disparados(disparado.categoria()).increment();
        atrasoDoDisparo(disparado.categoria()).record(disparado.atraso());
        log.atInfo()
                .addKeyValue("jornadaId", jornadaId)
                .addKeyValue("marcoId", disparado.marcoId())
                .addKeyValue("categoria", disparado.categoria().name())
                .addKeyValue("sequencia", disparado.sequencia())
                .addKeyValue("atrasoMs", disparado.atraso().toMillis())
                .log("Marco disparado");
    }

    private void registrarEncerramento(UUID jornadaId, MarcoEncerrado encerrado) {
        encerrados(encerrado.categoria(), encerrado.status()).increment();
        log.atInfo()
                .addKeyValue("jornadaId", jornadaId)
                .addKeyValue("marcoId", encerrado.marcoId())
                .addKeyValue("categoria", encerrado.categoria().name())
                .addKeyValue("sequencia", encerrado.sequencia())
                .addKeyValue("status", encerrado.status().name())
                .log("Marco encerrado");
    }

    private void registrarCorrecao(UUID jornadaId, MarcoCorrigido corrigido) {
        corrigidos(corrigido.categoria(), corrigido.para()).increment();
        log.atInfo()
                .addKeyValue("jornadaId", jornadaId)
                .addKeyValue("marcoId", corrigido.marcoId())
                .addKeyValue("categoria", corrigido.categoria().name())
                .addKeyValue("sequencia", corrigido.sequencia())
                .addKeyValue("de", corrigido.de().name())
                .addKeyValue("para", corrigido.para().name())
                .log("Marco corrigido");
    }
}
