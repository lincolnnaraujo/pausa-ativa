package br.com.pausaativa.agenda.adapter.in.web;

import br.com.pausaativa.agenda.application.port.out.JornadaAlterada;
import br.com.pausaativa.agenda.domain.MarcoDisparado;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

/**
 * Conexões SSE abertas pelas abas do frontend (spec H2, seção 7). Cada alteração da jornada vira, depois
 * do commit, um {@code marco-disparado} por marco disparado e um {@code jornada-atualizada}.
 */
@Component
class CanalDeEventos {

    static final String MARCO_DISPARADO = "marco-disparado";
    static final String JORNADA_ATUALIZADA = "jornada-atualizada";

    private static final Logger log = LoggerFactory.getLogger(CanalDeEventos.class);

    private final Set<SseEmitter> conexoes = ConcurrentHashMap.newKeySet();

    CanalDeEventos(MeterRegistry metricas) {
        Gauge.builder("pausaativa.sse.conexoes", conexoes, Set::size)
                .description("Abas do frontend conectadas ao stream de eventos")
                .register(metricas);
    }

    /** Sem timeout: a aba fica conectada o dia todo. O comentário inicial envia os cabeçalhos na hora. */
    SseEmitter conectar() {
        SseEmitter conexao = new SseEmitter(0L);
        conexao.onCompletion(() -> conexoes.remove(conexao));
        conexao.onTimeout(() -> conexoes.remove(conexao));
        conexao.onError(erro -> conexoes.remove(conexao));
        conexoes.add(conexao);
        enviar(conexao, SseEmitter.event().comment("conectado"));
        return conexao;
    }

    @TransactionalEventListener(fallbackExecution = true)
    void aoAlterarJornada(JornadaAlterada alteracao) {
        JornadaResposta jornada = JornadaResposta.de(alteracao.situacao());
        alteracao.eventos().stream()
                .filter(MarcoDisparado.class::isInstance)
                .map(MarcoDisparado.class::cast)
                .flatMap(disparado ->
                        jornada.marcos().stream().filter(marco -> marco.id().equals(disparado.marcoId())))
                .forEach(marco -> transmitir(
                        () -> SseEmitter.event().name(MARCO_DISPARADO).data(marco, MediaType.APPLICATION_JSON)));
        transmitir(() -> SseEmitter.event().name(JORNADA_ATUALIZADA).data(jornada, MediaType.APPLICATION_JSON));
    }

    /** Mantém as conexões vivas através do nginx e descobre abas fechadas. */
    @Scheduled(fixedRate = 20_000, initialDelay = 20_000)
    void manterConexoesVivas() {
        transmitir(() -> SseEmitter.event().comment("ping"));
    }

    /**
     * Fecha as abas ao encerrar o backend (spec H5, seção 3.4). O encerramento gracioso, que vem depois deste
     * evento, espera as requisições ativas terminarem, e uma conexão SSE nunca termina sozinha: o backend seria
     * morto pelo {@code docker stop}. As abas reconectam quando ele volta.
     */
    @EventListener(ContextClosedEvent.class)
    void fecharConexoes() {
        conexoes.forEach(SseEmitter::complete);
        conexoes.clear();
    }

    int quantidadeDeConexoes() {
        return conexoes.size();
    }

    /** Um evento novo por conexão: o {@link SseEventBuilder} não pode ser reaproveitado. */
    private void transmitir(Supplier<SseEventBuilder> evento) {
        conexoes.forEach(conexao -> enviar(conexao, evento.get()));
    }

    private void enviar(SseEmitter conexao, SseEventBuilder evento) {
        try {
            conexao.send(evento);
        } catch (IOException | IllegalStateException abaFechada) {
            conexoes.remove(conexao);
            log.debug("Conexão SSE removida: {}", abaFechada.getMessage());
        }
    }
}
