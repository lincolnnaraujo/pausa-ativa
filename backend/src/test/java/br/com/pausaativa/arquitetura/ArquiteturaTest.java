package br.com.pausaativa.arquitetura;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Aplica as regras de arquitetura ao código de produção. Qualquer violação quebra a build. */
@AnalyzeClasses(packages = ArquiteturaTest.BASE, importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    static final String BASE = "br.com.pausaativa";

    @ArchTest
    static final ArchRule r1 = RegrasDeArquitetura.dominioSemFrameworks(BASE);

    @ArchTest
    static final ArchRule r2 = RegrasDeArquitetura.dominioNaoDependeDeAplicacaoNemAdapters(BASE);

    @ArchTest
    static final ArchRule r3 = RegrasDeArquitetura.aplicacaoNaoDependeDeAdapters(BASE);

    @ArchTest
    static final ArchRule r4 = RegrasDeArquitetura.modulosSoSeAcessamPelaPortaDeEntrada(BASE);

    @ArchTest
    static final ArchRule r5 = RegrasDeArquitetura.modulosSemCiclos(BASE);
}
