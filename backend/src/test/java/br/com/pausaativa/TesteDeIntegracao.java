package br.com.pausaativa;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Instant;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Teste de integração com a aplicação inteira, Postgres real e {@link RelogioDeTeste} no lugar do
 * relógio do sistema. Todos usam a mesma configuração, então compartilham um contexto e um container.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@Import({TestcontainersConfiguration.class, TesteDeIntegracao.Relogio.class})
public @interface TesteDeIntegracao {

    /** 09:00 de 02/10/2026 em São Paulo. Cada teste ajusta o relógio para o que precisar. */
    Instant INSTANTE_INICIAL = Instant.parse("2026-10-02T12:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    class Relogio {

        @Bean
        @Primary
        RelogioDeTeste relogioDeTeste() {
            return new RelogioDeTeste(INSTANTE_INICIAL);
        }
    }
}
