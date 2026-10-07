package br.com.pausaativa.agenda.adapter.out.metricas;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * As séries nascem zeradas na subida (spec H5, seção 3.2): criada só no primeiro evento, a série já
 * começaria em 1, e o {@code increase()} do painel perderia esse evento.
 */
class ObservabilidadeDaAgendaTest {

    private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();

    private List<String> tags(String nome, String... chaves) {
        return metricas.find(nome).meters().stream()
                .map(Meter::getId)
                .map(id -> String.join(
                        "/", List.of(chaves).stream().map(id::getTag).toList()))
                .sorted()
                .toList();
    }

    @Test
    void naSubidaCriaCadaContadorZeradoComAsTagsQueOsEventosUsam() {
        new ObservabilidadeDaAgenda(metricas);

        assertThat(tags("pausaativa.marcos.encerrados", "categoria", "status"))
                .containsExactly(
                        "EXERCICIO/CONCLUIDO",
                        "EXERCICIO/FALHA",
                        "EXERCICIO/NAO_CONCLUIDO",
                        "EXERCICIO/NAO_ENTREGUE",
                        "HIDRATACAO/CONCLUIDO",
                        "HIDRATACAO/FALHA",
                        "HIDRATACAO/NAO_CONCLUIDO",
                        "HIDRATACAO/NAO_ENTREGUE");
        assertThat(tags("pausaativa.marcos.disparados", "categoria")).containsExactly("EXERCICIO", "HIDRATACAO");
        assertThat(tags("pausaativa.marcos.corrigidos", "categoria", "para"))
                .containsExactly("EXERCICIO/CONCLUIDO", "EXERCICIO/FALHA", "HIDRATACAO/CONCLUIDO", "HIDRATACAO/FALHA");
        assertThat(tags("pausaativa.blocos.montados", "duracao", "compensa_adiamento"))
                .containsExactly("10/false", "10/true", "5/false");
        assertThat(tags("pausaativa.marcos.atraso.disparo", "categoria")).containsExactly("EXERCICIO", "HIDRATACAO");
        assertThat(metricas.find("pausaativa.marcos.adiados").counter()).isNotNull();
        assertThat(metricas.find("pausaativa.blocos.itens").summary()).isNotNull();
        assertThat(metricas.find("pausaativa.marcos.encerrados").counters())
                .extracting(Counter::count)
                .containsOnly(0.0);
    }
}
