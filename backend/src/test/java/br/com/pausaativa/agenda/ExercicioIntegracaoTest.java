package br.com.pausaativa.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.pausaativa.RelogioDeTeste;
import br.com.pausaativa.TesteDeIntegracao;
import br.com.pausaativa.agenda.application.PerfilAusenteException;
import br.com.pausaativa.agenda.application.port.in.AvancarAgenda;
import br.com.pausaativa.agenda.application.port.in.ConsultarJornadaAtual;
import br.com.pausaativa.agenda.application.port.in.IniciarJornada;
import br.com.pausaativa.agenda.application.port.in.ResponderMarco;
import br.com.pausaativa.agenda.application.port.in.SituacaoDaJornada;
import br.com.pausaativa.agenda.application.port.in.SituacaoDoBloco;
import br.com.pausaativa.agenda.application.port.in.SituacaoDoMarco;
import br.com.pausaativa.agenda.application.port.out.JornadaAlterada;
import br.com.pausaativa.agenda.domain.Categoria;
import br.com.pausaativa.agenda.domain.DuracaoDoBlocoInvalidaException;
import br.com.pausaativa.agenda.domain.MarcoAdiado;
import br.com.pausaativa.agenda.domain.StatusMarco;
import br.com.pausaativa.treino.application.port.in.SalvarPerfil;
import br.com.pausaativa.treino.domain.Articulacao;
import br.com.pausaativa.treino.domain.Nivel;
import br.com.pausaativa.treino.domain.PerfilFisico;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** Marcos de exercício com o Treino e o Postgres de verdade (spec H3, T4). */
@TesteDeIntegracao
@RecordApplicationEvents
class ExercicioIntegracaoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 2);
    private static final PerfilFisico INICIANTE_SEM_EQUIPAMENTO =
            new PerfilFisico(Set.of(), Nivel.INICIANTE, Set.of(), true);

    @Autowired
    RelogioDeTeste relogio;

    @Autowired
    IniciarJornada iniciarJornada;

    @Autowired
    ConsultarJornadaAtual consultarJornadaAtual;

    @Autowired
    ResponderMarco responderMarco;

    @Autowired
    AvancarAgenda avancarAgenda;

    @Autowired
    SalvarPerfil salvarPerfil;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationEvents eventosPublicados;

    @Autowired
    MeterRegistry metricas;

    @BeforeEach
    void comecarDoZero() {
        jdbc.execute("truncate jornada cascade");
        salvarPerfil.salvar(INICIANTE_SEM_EQUIPAMENTO);
        relogioAs("09:00");
    }

    private void relogioAs(String hora) {
        relogio.ajustarPara(LocalDateTime.of(HOJE, LocalTime.parse(hora))
                .atZone(RelogioDeTeste.SAO_PAULO)
                .toInstant());
    }

    private SituacaoDaJornada atual() {
        return consultarJornadaAtual.consultar().orElseThrow();
    }

    private static SituacaoDoMarco exercicio(SituacaoDaJornada situacao, int sequencia) {
        return situacao.marcos().stream()
                .filter(marco -> marco.categoria() == Categoria.EXERCICIO && marco.sequencia() == sequencia)
                .findFirst()
                .orElseThrow();
    }

    private static List<String> nomes(SituacaoDoBloco bloco) {
        return bloco.exercicios().stream().map(SituacaoDoBloco.Exercicio::nome).toList();
    }

    @Test
    void cenario6SemPerfilODiaNaoComecaENadaEGravado() {
        jdbc.execute("truncate perfil_fisico cascade");

        assertThatThrownBy(() -> iniciarJornada.iniciar(3_000, 5))
                .isInstanceOf(PerfilAusenteException.class)
                .hasMessageContaining("perfil físico");
        assertThat(jdbc.queryForObject("select count(*) from jornada", Integer.class))
                .isZero();
    }

    @Test
    void duracaoForaDeCincoOuDezERecusada() {
        assertThatThrownBy(() -> iniciarJornada.iniciar(3_000, 15)).isInstanceOf(DuracaoDoBlocoInvalidaException.class);
        assertThat(jdbc.queryForObject("select count(*) from jornada", Integer.class))
                .isZero();
    }

    @Test
    void oBlocoDoTreinoFicaGravadoNoMarcoEVoltaIgualNaConsulta() {
        iniciarJornada.iniciar(3_000, 5);
        relogioAs("10:00");

        avancarAgenda.avancar();

        SituacaoDoBloco bloco = exercicio(atual(), 1).bloco();
        // O exemplo da seção 3.3 da spec, calculado à mão na T1.
        assertThat(nomes(bloco))
                .containsExactly(
                        "Sentar e levantar da cadeira",
                        "Flexão na parede",
                        "Anjo na parede",
                        "Prancha",
                        "Ponte de glúteo",
                        "Mobilidade torácica");
        assertThat(bloco.segundosEstimados()).isEqualTo(278);
        assertThat(bloco.duracaoMin()).isEqualTo(5);
        assertThat(bloco.compensaAdiamento()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from item_do_bloco", Integer.class))
                .isEqualTo(6);
        assertThat(jdbc.queryForObject(
                        "select duracao_bloco_min from marco where categoria = 'EXERCICIO' and sequencia = 1",
                        Integer.class))
                .isEqualTo(5);
    }

    @Test
    void cenario1UmDiaInteiroComPerfilQuePoupaJoelhoSemHalteresNaoPropoeOProibido() {
        salvarPerfil.salvar(new PerfilFisico(Set.of(Articulacao.JOELHO), Nivel.INTERMEDIARIO, Set.of(), true));
        iniciarJornada.iniciar(3_000, 10);

        for (int hora = 1; hora <= 8; hora++) {
            relogio.avancar(Duration.ofHours(1));
            avancarAgenda.avancar();
        }

        List<SituacaoDoBloco> blocos = atual().marcos().stream()
                .filter(marco -> marco.categoria() == Categoria.EXERCICIO)
                .map(SituacaoDoMarco::bloco)
                .toList();
        assertThat(blocos).hasSize(8).allSatisfy(bloco -> {
            assertThat(bloco.duracaoMin()).isEqualTo(10);
            assertThat(bloco.segundosEstimados()).isLessThanOrEqualTo(600);
            assertThat(nomes(bloco))
                    .doesNotContain(
                            "Agachamento livre",
                            "Afundo alternado",
                            "Remada curvada",
                            "Desenvolvimento de ombros",
                            "Elevação lateral",
                            "Rosca direta");
        });
    }

    @Test
    void cenario3E4AdiarGravaOAdiadoEConcluirOSeguinteConcluiOsDois() {
        double adiadosAntes = contador("pausaativa.marcos.adiados");
        double compensacoesAntes = contador("pausaativa.blocos.montados", "compensa_adiamento", "true");
        iniciarJornada.iniciar(3_000, 5);
        relogioAs("10:00");
        avancarAgenda.avancar();
        UUID primeiro = exercicio(atual(), 1).id();
        eventosPublicados.clear();

        responderMarco.adiar(primeiro);

        assertThat(exercicio(atual(), 1).status()).isEqualTo(StatusMarco.ADIADO);
        assertThat(eventosPublicados.stream(JornadaAlterada.class))
                .singleElement()
                .satisfies(alteracao -> assertThat(alteracao.eventos())
                        .filteredOn(MarcoAdiado.class::isInstance)
                        .singleElement()
                        .isEqualTo(new MarcoAdiado(primeiro, Categoria.EXERCICIO, 1)));
        assertThat(contador("pausaativa.marcos.adiados")).isEqualTo(adiadosAntes + 1);

        relogioAs("11:00");
        avancarAgenda.avancar();
        SituacaoDoMarco seguinte = exercicio(atual(), 2);
        assertThat(seguinte.bloco().duracaoMin()).isEqualTo(10);
        assertThat(seguinte.bloco().compensaAdiamento()).isTrue();
        assertThat(seguinte.mensagem()).endsWith("Inclui o bloco adiado.");
        assertThat(contador("pausaativa.blocos.montados", "compensa_adiamento", "true"))
                .isEqualTo(compensacoesAntes + 1);

        responderMarco.concluir(seguinte.id());

        assertThat(exercicio(atual(), 1).status()).isEqualTo(StatusMarco.CONCLUIDO);
        assertThat(exercicio(atual(), 2).status()).isEqualTo(StatusMarco.CONCLUIDO);
    }

    @Test
    void blocosDoDiaVariamEOsItensSaoContadosNaMetrica() {
        long blocosAntes = itensRegistrados();
        iniciarJornada.iniciar(3_000, 5);
        relogioAs("10:00");
        avancarAgenda.avancar();
        relogioAs("11:00");
        avancarAgenda.avancar();

        SituacaoDaJornada situacao = atual();
        assertThat(nomes(exercicio(situacao, 2).bloco()).getFirst())
                .isNotEqualTo(nomes(exercicio(situacao, 1).bloco()).getFirst());
        assertThat(itensRegistrados()).isEqualTo(blocosAntes + 2);
    }

    private long itensRegistrados() {
        return metricas.find("pausaativa.blocos.itens").summaries().stream()
                .mapToLong(resumo -> resumo.count())
                .sum();
    }

    private double contador(String nome, String... tags) {
        return metricas.find(nome).tags(tags).counters().stream()
                .mapToDouble(contador -> contador.count())
                .sum();
    }
}
