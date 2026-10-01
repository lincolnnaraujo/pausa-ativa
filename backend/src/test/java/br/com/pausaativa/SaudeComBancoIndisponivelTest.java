package br.com.pausaativa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Cenário 3 da H1: com o banco fora, o health fica DOWN e volta a UP sozinho quando o banco retorna.
 *
 * <p>O container do Postgres é congelado com {@code docker pause}, que simula um banco que não
 * responde sem trocar a porta mapeada (um stop/start trocaria e quebraria a URL do datasource).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SaudeComBancoIndisponivelTest {

    @Autowired
    WebApplicationContext contexto;

    @Autowired
    PostgreSQLContainer postgres;

    private boolean pausado;

    @Test
    void healthCaiSemBancoEVoltaSozinhoQuandoOBancoRetorna() {
        MockMvcTester mvc = MockMvcTester.from(contexto);
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();

        pausarBanco();

        // Intervalo acima de 500 ms: o Hikari só revalida conexões ociosas há mais que isso.
        await().pollDelay(Duration.ofSeconds(1))
                .pollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(mvc.get().uri("/actuator/health"))
                        .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .bodyJson()
                        .extractingPath("$.components.db.status")
                        .isEqualTo("DOWN"));

        // A liveness não depende do banco: o Docker não marca o container como doente (spec, seção 7).
        assertThat(mvc.get().uri("/actuator/health/liveness")).hasStatusOk();
        assertThat(mvc.get().uri("/actuator/health/readiness")).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);

        retomarBanco();

        await().pollInterval(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(mvc.get().uri("/actuator/health"))
                        .hasStatusOk()
                        .bodyJson()
                        .extractingPath("$.status")
                        .isEqualTo("UP"));
    }

    /** O contexto e o container são compartilhados com as outras classes: nunca deixar o banco congelado. */
    @AfterEach
    void garantirBancoNoAr() {
        if (pausado) {
            retomarBanco();
        }
    }

    private void pausarBanco() {
        postgres.getDockerClient().pauseContainerCmd(postgres.getContainerId()).exec();
        pausado = true;
    }

    private void retomarBanco() {
        postgres.getDockerClient()
                .unpauseContainerCmd(postgres.getContainerId())
                .exec();
        pausado = false;
    }
}
