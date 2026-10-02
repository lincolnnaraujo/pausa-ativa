package br.com.pausaativa.shared.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio único da aplicação. Todo acesso ao tempo passa por este {@link Clock}, para os testes
 * controlarem o instante e o fuso de negócio ficar num lugar só. A JVM e o banco trabalham em UTC.
 *
 * <p>Precisão de milissegundos: o relógio do sistema tem nanossegundos, mas o Postgres guarda
 * microssegundos. Sem o corte, a resposta de um comando e a consulta seguinte mostrariam horários
 * ligeiramente diferentes para o mesmo instante.
 */
@Configuration(proxyBeanMethods = false)
public class RelogioConfig {

    static final ZoneId FUSO_DE_NEGOCIO = ZoneId.of("America/Sao_Paulo");

    @Bean
    Clock clock() {
        return Clock.tickMillis(FUSO_DE_NEGOCIO);
    }
}
