package br.com.pausaativa;

import org.springframework.boot.SpringApplication;

/**
 * Sobe o backend localmente com um Postgres em container, sem docker-compose.
 *
 * <p>Uso: {@code ./mvnw spring-boot:test-run}
 */
public class TestPausaAtivaApplication {

    public static void main(String[] args) {
        SpringApplication.from(PausaAtivaApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
