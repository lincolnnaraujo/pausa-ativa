package br.com.pausaativa.sistema.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(StatusController.class)
@Import(StatusControllerTest.Config.class)
class StatusControllerTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        /** 12:00 UTC é 09:00 em São Paulo. */
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-01T12:00:00.123Z"), ZoneId.of("America/Sao_Paulo"));
        }

        @Bean
        BuildProperties buildProperties() {
            Properties propriedades = new Properties();
            propriedades.setProperty("version", "0.1.0");
            return new BuildProperties(propriedades);
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Test
    void respondeOContratoComHorarioNoFusoDeNegocioSemFracaoDeSegundo() {
        assertThat(mvc.get().uri("/api/v1/sistema/status"))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        {
                          "aplicacao": "pausa-ativa",
                          "versao": "0.1.0",
                          "agora": "2026-10-01T09:00:00-03:00",
                          "fuso": "America/Sao_Paulo"
                        }
                        """);
    }

    @Test
    void semBuildInfoAVersaoApareceComoDesconhecida() {
        var semBuildInfo = new StaticListableBeanFactory().getBeanProvider(BuildProperties.class);

        var resposta = new StatusController(Clock.systemUTC(), semBuildInfo).status();

        assertThat(resposta.versao()).isEqualTo(StatusController.VERSAO_DESCONHECIDA);
    }
}
