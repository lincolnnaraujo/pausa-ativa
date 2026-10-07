package br.com.pausaativa.historico.adapter.out.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.pausaativa.agenda.application.port.in.ConsultarRegistrosDiarios.Contagem;
import br.com.pausaativa.agenda.application.port.in.ConsultarRegistrosDiarios.RegistroDiario;
import br.com.pausaativa.historico.domain.Categoria;
import br.com.pausaativa.historico.domain.ContagemPorSituacao;
import br.com.pausaativa.historico.domain.EstadoDaJornada;
import br.com.pausaativa.historico.domain.RegistroDoDia;
import br.com.pausaativa.historico.domain.Situacao;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tradução das contagens da Agenda para o domínio do Histórico (spec H4, seção 3.1). */
class RegistrosDiariosDaAgendaTest {

    @Test
    void agendadoPendenteEAdiadoFicamEmAberto() {
        assertThat(RegistrosDiariosDaAgenda.situacao("CONCLUIDO")).isEqualTo(Situacao.CONCLUIDO);
        assertThat(RegistrosDiariosDaAgenda.situacao("FALHA")).isEqualTo(Situacao.FALHA);
        assertThat(RegistrosDiariosDaAgenda.situacao("NAO_ENTREGUE")).isEqualTo(Situacao.NAO_ENTREGUE);
        assertThat(RegistrosDiariosDaAgenda.situacao("NAO_CONCLUIDO")).isEqualTo(Situacao.NAO_CONCLUIDO);
        assertThat(List.of("AGENDADO", "PENDENTE", "ADIADO"))
                .allSatisfy(status ->
                        assertThat(RegistrosDiariosDaAgenda.situacao(status)).isEqualTo(Situacao.EM_ABERTO));
        assertThatThrownBy(() -> RegistrosDiariosDaAgenda.situacao("OUTRA")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void somaAsContagensEmAbertoDaMesmaCategoria() {
        LocalDate dia = LocalDate.of(2026, 10, 2);
        RegistrosDiariosDaAgenda adapter = new RegistrosDiariosDaAgenda((de, ate) -> List.of(new RegistroDiario(
                dia,
                "EM_ANDAMENTO",
                List.of(
                        new Contagem("HIDRATACAO", "CONCLUIDO", 3),
                        new Contagem("HIDRATACAO", "PENDENTE", 1),
                        new Contagem("HIDRATACAO", "AGENDADO", 12),
                        new Contagem("EXERCICIO", "AGENDADO", 8)))));

        List<RegistroDoDia> registros = adapter.entre(dia, dia);

        assertThat(registros).singleElement().satisfies(registro -> {
            assertThat(registro.jornada()).isEqualTo(EstadoDaJornada.EM_ANDAMENTO);
            assertThat(registro.contagem(Categoria.HIDRATACAO)).isEqualTo(new ContagemPorSituacao(3, 0, 0, 0, 13));
            assertThat(registro.contagem(Categoria.EXERCICIO)).isEqualTo(new ContagemPorSituacao(0, 0, 0, 0, 8));
        });
    }
}
