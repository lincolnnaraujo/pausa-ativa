package br.com.pausaativa.agenda.adapter.in.demonstracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.pausaativa.agenda.application.port.in.CriarHistoricoDeExemplo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class HistoricoDeExemploNaSubidaTest {

    private final CriarHistoricoDeExemplo criarHistorico = mock(CriarHistoricoDeExemplo.class);

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withBean(CriarHistoricoDeExemplo.class, () -> criarHistorico)
            .withUserConfiguration(HistoricoDeExemploNaSubida.class);

    @Test
    void soExisteComAPropriedadeDaDemonstracaoLigada() {
        contexto.run(sem -> assertThat(sem).doesNotHaveBean(HistoricoDeExemploNaSubida.class));
        contexto.withPropertyValues("pausa-ativa.demonstracao.historico=false")
                .run(desligada -> assertThat(desligada).doesNotHaveBean(HistoricoDeExemploNaSubida.class));
        contexto.withPropertyValues("pausa-ativa.demonstracao.historico=true")
                .run(ligada -> assertThat(ligada).hasSingleBean(HistoricoDeExemploNaSubida.class));
    }

    @Test
    void criaOHistoricoNaSubidaEUmaFalhaNaoDerrubaADemonstracao() {
        doThrow(new IllegalStateException("banco fora do ar"))
                .when(criarHistorico)
                .criar();

        assertThatCode(new HistoricoDeExemploNaSubida(criarHistorico)::aoSubir).doesNotThrowAnyException();
        verify(criarHistorico).criar();
    }
}
