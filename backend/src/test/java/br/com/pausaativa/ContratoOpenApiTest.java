package br.com.pausaativa;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * O contrato versionado em {@code docs/api/openapi.json} é igual ao que o código gera.
 *
 * <p>Se uma mudança na API for intencional, regenere o arquivo e revise o diff:
 * {@code ./mvnw verify -Dopenapi.atualizar=true}
 */
@TesteDeIntegracao
class ContratoOpenApiTest {

    private static final Path CONTRATO = Path.of("..", "docs", "api", "openapi.json");

    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    WebApplicationContext contexto;

    @Test
    void contratoVersionadoEstaAtualizado() throws IOException {
        String corpo = MockMvcTester.from(contexto)
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .getResponse()
                .getContentAsString();
        JsonNode gerado = json.readTree(corpo);

        if (Boolean.getBoolean("openapi.atualizar")) {
            Files.createDirectories(CONTRATO.getParent());
            String formatado = json.writerWithDefaultPrettyPrinter().writeValueAsString(gerado);
            Files.writeString(CONTRATO, formatado.replace("\r\n", "\n") + "\n");
        }

        assertThat(CONTRATO)
                .as("docs/api/openapi.json não existe. Gere com ./mvnw verify -Dopenapi.atualizar=true")
                .exists();
        assertThat(json.readTree(Files.readString(CONTRATO)))
                .as("A API mudou e docs/api/openapi.json está desatualizado. "
                        + "Revise e regenere com ./mvnw verify -Dopenapi.atualizar=true")
                .isEqualTo(gerado);
    }
}
