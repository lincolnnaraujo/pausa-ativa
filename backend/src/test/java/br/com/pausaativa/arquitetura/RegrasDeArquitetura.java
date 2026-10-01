package br.com.pausaativa.arquitetura;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Regras de arquitetura da spec H1 (seção 5.2).
 *
 * <p>Recebem o pacote base para valerem tanto no código real ({@link ArquiteturaTest}) quanto nas
 * fixtures que as violam de propósito ({@link RegrasDeArquiteturaTest}). A R6 veio na H2.
 */
final class RegrasDeArquitetura {

    static final List<String> MODULOS = List.of("agenda", "treino", "historico", "sistema");

    private RegrasDeArquitetura() {}

    /** R1: o domínio é Java puro. */
    static ArchRule dominioSemFrameworks(String base) {
        return noClasses()
                .that()
                .resideInAPackage(base + "..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "com.fasterxml.jackson..",
                        "tools.jackson..")
                .as("R1: o domínio não depende de Spring, JPA, Hibernate nem Jackson");
    }

    /** R2: o domínio não conhece as camadas de fora. */
    static ArchRule dominioNaoDependeDeAplicacaoNemAdapters(String base) {
        return noClasses()
                .that()
                .resideInAPackage(base + "..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(base + "..application..", base + "..adapter..")
                .as("R2: o domínio não depende de application nem de adapter");
    }

    /** R3: os casos de uso falam com o mundo externo só pelas portas de saída. */
    static ArchRule aplicacaoNaoDependeDeAdapters(String base) {
        return noClasses()
                .that()
                .resideInAPackage(base + "..application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(base + "..adapter..", "jakarta.persistence..")
                .as("R3: application não depende de adapter nem de JPA");
    }

    /** R4: o {@code application.port.in} é a única API pública de cada módulo. */
    static ArchRule modulosSoSeAcessamPelaPortaDeEntrada(String base) {
        return CompositeArchRule.of(MODULOS.stream()
                        .map(modulo -> acessoExternoAoModulo(base, modulo))
                        .toList())
                .as("R4: um módulo só acessa outro pelo application.port.in dele");
    }

    private static ArchRule acessoExternoAoModulo(String base, String modulo) {
        String pacote = base + "." + modulo;
        return noClasses()
                .that()
                .resideOutsideOfPackage(pacote + "..")
                .should()
                .dependOnClassesThat(
                        resideInAPackage(pacote + "..").and(not(resideInAPackage(pacote + ".application.port.in.."))))
                .as("ninguém de fora de " + modulo + " acessa " + modulo + " fora do application.port.in");
    }

    /** R5: sem ciclos entre os pacotes de primeiro nível (módulos e shared). */
    static ArchRule modulosSemCiclos(String base) {
        return slices().matching(base + ".(*)..").should().beFreeOfCycles().as("R5: sem ciclos entre módulos");
    }

    /**
     * R6: o tempo vem sempre de um {@link java.time.Clock} injetado, para os testes controlarem o
     * instante e o fuso de negócio ficar num lugar só. As versões de {@code now(Clock)} são permitidas.
     */
    static ArchRule tempoSoPeloClock(String base) {
        return noClasses()
                .that()
                .resideInAPackage(base + "..")
                .should()
                .callMethod(Instant.class, "now")
                .orShould()
                .callMethod(LocalDate.class, "now")
                .orShould()
                .callMethod(LocalDateTime.class, "now")
                .orShould()
                .callMethod(LocalTime.class, "now")
                .orShould()
                .callMethod(ZonedDateTime.class, "now")
                .orShould()
                .callMethod(OffsetDateTime.class, "now")
                .orShould()
                .callMethod(System.class, "currentTimeMillis")
                .as("R6: o tempo vem de um Clock injetado, nunca do relógio do sistema");
    }
}
