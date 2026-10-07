package br.com.pausaativa.agenda.adapter.in.demonstracao;

import br.com.pausaativa.agenda.application.port.in.CriarHistoricoDeExemplo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Cria o histórico de exemplo na subida (spec H4, seção 9). Só existe com
 * {@code pausa-ativa.demonstracao.historico=true}, que o {@code docker-compose.demo.yml} liga; o uso
 * diário nunca liga.
 */
@Component
@ConditionalOnProperty(name = "pausa-ativa.demonstracao.historico", havingValue = "true")
class HistoricoDeExemploNaSubida {

    private static final Logger log = LoggerFactory.getLogger(HistoricoDeExemploNaSubida.class);

    private final CriarHistoricoDeExemplo criarHistorico;

    HistoricoDeExemploNaSubida(CriarHistoricoDeExemplo criarHistorico) {
        this.criarHistorico = criarHistorico;
    }

    /** Uma falha aqui não derruba a demonstração: ela só começa sem histórico. */
    @EventListener(ApplicationReadyEvent.class)
    void aoSubir() {
        try {
            criarHistorico.criar();
        } catch (RuntimeException erro) {
            log.warn("Falha ao criar o histórico de exemplo; a demonstração segue sem ele", erro);
        }
    }
}
