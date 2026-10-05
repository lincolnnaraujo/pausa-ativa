package br.com.pausaativa.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
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

    /**
     * Propriedade anulável que aponta para outro schema sai como {@code oneOf: [$ref, null]}. O springdoc
     * põe {@code type: [object, null]} ao lado do {@code $ref}, e o openapi-typescript ignora o null nessa
     * forma: o tipo do frontend diria que o campo nunca é nulo.
     */
    @Bean
    OpenApiCustomizer referenciasAnulaveis() {
        return contrato -> {
            if (contrato.getComponents() == null || contrato.getComponents().getSchemas() == null) {
                return;
            }
            for (Schema<?> schema : contrato.getComponents().getSchemas().values()) {
                Map<String, Schema> propriedades = schema.getProperties();
                if (propriedades != null) {
                    propriedades.replaceAll((nome, propriedade) ->
                            referenciaAnulavel(propriedade) ? comoOneOf(propriedade) : propriedade);
                }
            }
        };
    }

    private static boolean referenciaAnulavel(Schema<?> propriedade) {
        return propriedade.get$ref() != null
                && propriedade.getTypes() != null
                && propriedade.getTypes().contains("null");
    }

    private static Schema<?> comoOneOf(Schema<?> propriedade) {
        return new JsonSchema()
                .description(propriedade.getDescription())
                .oneOf(List.of(new JsonSchema().$ref(propriedade.get$ref()), new JsonSchema().types(Set.of("null"))));
    }
}
