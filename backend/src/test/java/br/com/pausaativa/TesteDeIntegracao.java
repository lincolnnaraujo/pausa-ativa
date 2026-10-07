package br.com.pausaativa;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Instant;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Teste de integração com a aplicação inteira, Postgres real e {@link RelogioDeTeste} no lugar do
 * relógio do sistema. Todos usam a mesma configuração, então compartilham um contexto e um container.
 *
 * <p>Com o {@link AutoConfigureMetrics}, as métricas saem como em produção, inclusive no
 * {@code /actuator/prometheus}, que o Spring Boot desliga nos testes por padrão (spec H5, seção 5). O
 * {@link AutoConfigureMockMvc} oferece um {@code MockMvcTester} com os filtros da aplicação, entre eles
 * o que mede o {@code http.server.requests}; o {@code MockMvcTester.from(contexto)} não passa por eles.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMetrics
@AutoConfigureMockMvc
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
