package br.com.pausaativa.arquitetura;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * Prova que cada regra de arquitetura pega a violação que deve pegar.
 *
 * <p>As fixtures ficam em {@code fixtures.arquitetura.rN}, fora de {@code br.com.pausaativa}, para
 * o component scan do Spring nunca carregá-las nos testes de integração.
 */
class RegrasDeArquiteturaTest {

    private static final String FIXTURES = "fixtures.arquitetura";

    private static JavaClasses importar(String base) {
        return new ClassFileImporter().importPackages(base);
    }

    /** Cenário 2 da H1: classe de domínio que usa Spring quebra a build. */
    @Test
    void r1RecusaDominioAnotadoComSpring() {
        String base = FIXTURES + ".r1";

        assertThatThrownBy(() -> RegrasDeArquitetura.dominioSemFrameworks(base).check(importar(base)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("R1")
                .hasMessageContaining("MarcoComSpring")
                .hasMessageContaining("org.springframework.stereotype.Component");
    }

    @Test
    void r2RecusaDominioQueConheceAdapter() {
        String base = FIXTURES + ".r2";

        assertThatThrownBy(() -> RegrasDeArquitetura.dominioNaoDependeDeAplicacaoNemAdapters(base)
                        .check(importar(base)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("R2")
                .hasMessageContaining("MarcoQueConheceOAdapter");
    }

    @Test
    void r3RecusaCasoDeUsoQueUsaAdapterDireto() {
        String base = FIXTURES + ".r3";

        assertThatThrownBy(() ->
                        RegrasDeArquitetura.aplicacaoNaoDependeDeAdapters(base).check(importar(base)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("R3")
                .hasMessageContaining("IniciarJornadaService");
    }

    @Test
    void r4RecusaAcessoAoDominioDeOutroModuloMasAceitaAPortaDeEntrada() {
        String base = FIXTURES + ".r4";

        assertThatThrownBy(() -> RegrasDeArquitetura.modulosSoSeAcessamPelaPortaDeEntrada(base)
                        .check(importar(base)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("R4")
                .hasMessageContaining("agenda.domain.Jornada")
                .hasMessageNotContaining("ConsultarJornadas");
    }

    @Test
    void r5RecusaCicloEntreModulos() {
        String base = FIXTURES + ".r5";

        assertThatThrownBy(() -> RegrasDeArquitetura.modulosSemCiclos(base).check(importar(base)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("R5")
                .hasMessageContaining("IniciarJornada")
                .hasMessageContaining("MontarBloco");
    }

    @Test
    void r6RecusaLeituraDoRelogioDoSistema() {
        String base = FIXTURES + ".r6";

        assertThatThrownBy(() -> RegrasDeArquitetura.tempoSoPeloClock(base).check(importar(base)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("R6")
                .hasMessageContaining("MarcoComRelogioDoSistema")
                .hasMessageContaining("Instant.now()")
                .hasMessageNotContaining("MarcoComClock");
    }
}
