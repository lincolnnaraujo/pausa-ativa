package br.com.pausaativa.arquitetura;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import java.util.List;

/**
 * Regras de arquitetura da spec H1 (seção 5.2).
 *
 * <p>Recebem o pacote base para valerem tanto no código real ({@link ArquiteturaTest}) quanto nas
 * fixtures que as violam de propósito ({@link RegrasDeArquiteturaTest}).
 *
 * <p>{@code allowEmptyShould(true)} existe só enquanto os módulos não têm classes; sai na H2.
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
                .as("R1: o domínio não depende de Spring, JPA, Hibernate nem Jackson")
                .allowEmptyShould(true);
    }

    /** R2: o domínio não conhece as camadas de fora. */
    static ArchRule dominioNaoDependeDeAplicacaoNemAdapters(String base) {
        return noClasses()
                .that()
                .resideInAPackage(base + "..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(base + "..application..", base + "..adapter..")
                .as("R2: o domínio não depende de application nem de adapter")
                .allowEmptyShould(true);
    }

    /** R3: os casos de uso falam com o mundo externo só pelas portas de saída. */
    static ArchRule aplicacaoNaoDependeDeAdapters(String base) {
        return noClasses()
                .that()
                .resideInAPackage(base + "..application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(base + "..adapter..", "jakarta.persistence..")
                .as("R3: application não depende de adapter nem de JPA")
                .allowEmptyShould(true);
    }

    /** R4: o {@code application.port.in} é a única API pública de cada módulo. */
    static ArchRule modulosSoSeAcessamPelaPortaDeEntrada(String base) {
        return CompositeArchRule.of(MODULOS.stream()
                        .map(modulo -> acessoExternoAoModulo(base, modulo))
                        .toList())
                .as("R4: um módulo só acessa outro pelo application.port.in dele")
                .allowEmptyShould(true);
    }

    private static ArchRule acessoExternoAoModulo(String base, String modulo) {
        String pacote = base + "." + modulo;
        return noClasses()
                .that()
                .resideOutsideOfPackage(pacote + "..")
                .should()
                .dependOnClassesThat(resideInAPackage(pacote + "..")
                        .and(not(resideInAPackage(pacote + ".application.port.in.."))))
                .as("ninguém de fora de " + modulo + " acessa " + modulo + " fora do application.port.in")
                .allowEmptyShould(true);
    }

    /** R5: sem ciclos entre os pacotes de primeiro nível (módulos e shared). */
    static ArchRule modulosSemCiclos(String base) {
        return slices()
                .matching(base + ".(*)..")
                .should()
                .beFreeOfCycles()
                .as("R5: sem ciclos entre módulos")
                .allowEmptyShould(true);
    }
}
