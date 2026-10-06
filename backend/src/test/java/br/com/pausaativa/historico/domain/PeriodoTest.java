package br.com.pausaativa.historico.domain;

import static br.com.pausaativa.historico.domain.TipoDePeriodo.DIA;
import static br.com.pausaativa.historico.domain.TipoDePeriodo.MES;
import static br.com.pausaativa.historico.domain.TipoDePeriodo.SEMANA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Dia, semana de segunda a domingo (D2) e mês do calendário (spec H4, seção 3.2). */
class PeriodoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 6); // terça-feira

    @Test
    void diaEOProprioDia() {
        Periodo dia = Periodo.contendo(DIA, LocalDate.of(2026, 10, 5), HOJE);

        assertThat(dia).isEqualTo(new Periodo(DIA, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5)));
        assertThat(dia.dias()).containsExactly(LocalDate.of(2026, 10, 5));
    }

    @Test
    void semanaVaiDeSegundaADomingo() {
        Periodo semana = new Periodo(SEMANA, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4));

        assertThat(Periodo.contendo(SEMANA, LocalDate.of(2026, 9, 28), HOJE)).isEqualTo(semana); // segunda
        assertThat(Periodo.contendo(SEMANA, LocalDate.of(2026, 9, 30), HOJE)).isEqualTo(semana); // quarta
        assertThat(Periodo.contendo(SEMANA, LocalDate.of(2026, 10, 4), HOJE)).isEqualTo(semana); // domingo
        assertThat(semana.dias()).hasSize(7).startsWith(LocalDate.of(2026, 9, 28));
    }

    @Test
    void semanaAtualIncluiOsDiasQueAindaVaoChegar() {
        assertThat(Periodo.contendo(SEMANA, HOJE, HOJE))
                .isEqualTo(new Periodo(SEMANA, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11)));
    }

    @Test
    void semanaAtravessaAViradaDoAno() {
        assertThat(Periodo.contendo(SEMANA, LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 10)))
                .isEqualTo(new Periodo(SEMANA, LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3)));
    }

    @Test
    void mesVaiDoPrimeiroAoUltimoDia() {
        Periodo outubro = Periodo.contendo(MES, HOJE, HOJE);

        assertThat(outubro).isEqualTo(new Periodo(MES, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
        assertThat(outubro.dias()).hasSize(31);
        assertThat(Periodo.contendo(MES, LocalDate.of(2028, 2, 10), LocalDate.of(2028, 3, 1))
                        .dias())
                .hasSize(29)
                .endsWith(LocalDate.of(2028, 2, 29));
    }

    @Test
    void dataFuturaERecusadaEHojeNao() {
        assertThatThrownBy(() -> Periodo.contendo(DIA, HOJE.plusDays(1), HOJE))
                .isInstanceOf(DataFuturaException.class)
                .hasMessage("O histórico vai até hoje: 2026-10-07 ainda não chegou.");
        assertThat(Periodo.contendo(DIA, HOJE, HOJE).inicio()).isEqualTo(HOJE);
    }

    @Test
    void contemSoOsDiasDoIntervalo() {
        Periodo semana = new Periodo(SEMANA, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4));

        assertThat(semana.contem(LocalDate.of(2026, 9, 28))).isTrue();
        assertThat(semana.contem(LocalDate.of(2026, 10, 4))).isTrue();
        assertThat(semana.contem(LocalDate.of(2026, 9, 27))).isFalse();
        assertThat(semana.contem(LocalDate.of(2026, 10, 5))).isFalse();
    }

    @Test
    void fimAntesDoInicioERecusado() {
        assertThatThrownBy(() -> new Periodo(DIA, HOJE, HOJE.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
