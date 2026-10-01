package br.com.pausaativa.shared.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio único da aplicação. Todo acesso ao tempo passa por este {@link Clock}, para os testes
 * controlarem o instante e o fuso de negócio ficar num lugar só. A JVM e o banco trabalham em UTC.
 */
@Configuration(proxyBeanMethods = false)
public class RelogioConfig {

    static final ZoneId FUSO_DE_NEGOCIO = ZoneId.of("America/Sao_Paulo");

    @Bean
    Clock clock() {
        return Clock.system(FUSO_DE_NEGOCIO);
    }
}
