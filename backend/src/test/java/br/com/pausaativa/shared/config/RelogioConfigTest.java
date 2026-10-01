package br.com.pausaativa.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class RelogioConfigTest {

    @Test
    void relogioUsaOFusoDeSaoPaulo() {
        assertThat(new RelogioConfig().clock().getZone()).isEqualTo(ZoneId.of("America/Sao_Paulo"));
    }
}
