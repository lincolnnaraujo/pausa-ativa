package br.com.pausaativa.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados do contrato OpenAPI, gerado pelo springdoc a partir dos controllers. A cópia versionada
 * fica em {@code docs/api/openapi.json} e é conferida pelo {@code ContratoOpenApiTest}.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI contratoPausaAtiva(ObjectProvider<BuildProperties> buildProperties) {
        BuildProperties build = buildProperties.getIfAvailable();
        return new OpenAPI()
                .info(new Info()
                        .title("Pausa Ativa API")
                        .version(build != null ? build.getVersion() : "desconhecida")
                        .description("Jornada de trabalho com marcos de hidratação e exercício."))
                // URL relativa: o frontend e a API saem pela mesma origem, via nginx.
                .servers(List.of(new Server().url("/").description("Mesma origem do frontend")));
    }
}
