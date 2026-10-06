package br.com.pausaativa.treino;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.pausaativa.TesteDeIntegracao;
import br.com.pausaativa.treino.application.port.in.ConsultarPerfil;
import br.com.pausaativa.treino.application.port.in.MontarBloco;
import br.com.pausaativa.treino.application.port.in.SalvarPerfil;
import br.com.pausaativa.treino.application.port.out.CatalogoRepository;
import br.com.pausaativa.treino.domain.Articulacao;
import br.com.pausaativa.treino.domain.CatalogoDeTeste;
import br.com.pausaativa.treino.domain.Equipamento;
import br.com.pausaativa.treino.domain.Exercicio;
import br.com.pausaativa.treino.domain.Nivel;
import br.com.pausaativa.treino.domain.PerfilFisico;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Catálogo da migração {@code V3}, perfil no Postgres e a porta que a Agenda vai usar (spec H3, T2). */
@TesteDeIntegracao
class TreinoIntegracaoTest {

    @Autowired
    CatalogoRepository catalogo;

    @Autowired
    ConsultarPerfil consultarPerfil;

    @Autowired
    SalvarPerfil salvarPerfil;

    @Autowired
    MontarBloco montarBloco;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void semPerfil() {
        jdbc.execute("truncate perfil_fisico cascade");
    }

    @Test
    void aMigracaoGravaOCatalogoRevisadoComOUsuario() {
        List<Exercicio> doBanco = catalogo.todos();

        assertThat(doBanco).hasSameSizeAs(CatalogoDeTeste.EXERCICIOS);
        for (int i = 0; i < doBanco.size(); i++) {
            Exercicio banco = doBanco.get(i);
            Exercicio epico = CatalogoDeTeste.EXERCICIOS.get(i);
            assertThat(banco)
                    .as(epico.codigo())
                    .usingRecursiveComparison()
                    .ignoringFields("instrucao")
                    .isEqualTo(epico);
            assertThat(banco.instrucao()).isNotBlank().doesNotStartWith("Como fazer");
        }
    }

    @Test
    void semPerfilOBlocoNaoEMontado() {
        assertThat(consultarPerfil.preenchido()).isFalse();
        assertThat(consultarPerfil.consultar()).isEmpty();

        assertThatThrownBy(() -> montarBloco.montar(new MontarBloco.Pedido(Duration.ofMinutes(5), 1, List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("perfil");
    }

    @Test
    void perfilGravadoESubstituido() {
        salvarPerfil.salvar(new PerfilFisico(
                Set.of(Articulacao.JOELHO, Articulacao.PUNHO),
                Nivel.INTERMEDIARIO,
                Set.of(Equipamento.HALTERES_2KG),
                false));
        PerfilFisico novo = new PerfilFisico(Set.of(Articulacao.OMBRO), Nivel.INICIANTE, Set.of(), true);

        salvarPerfil.salvar(novo);

        assertThat(consultarPerfil.preenchido()).isTrue();
        assertThat(consultarPerfil.consultar()).contains(novo);
        assertThat(jdbc.queryForObject("select count(*) from perfil_fisico", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void comOCatalogoDoBancoMontaOExemploDaSpec() {
        salvarPerfil.salvar(new PerfilFisico(Set.of(), Nivel.INICIANTE, Set.of(), true));

        MontarBloco.BlocoMontado bloco =
                montarBloco.montar(new MontarBloco.Pedido(Duration.ofMinutes(5), 1, List.of()));

        assertThat(bloco.itens())
                .extracting(MontarBloco.Item::codigo)
                .containsExactly(
                        "sentar-e-levantar",
                        "flexao-na-parede",
                        "anjo-na-parede",
                        "prancha",
                        "ponte-de-gluteo",
                        "mobilidade-toracica");
        assertThat(bloco.estimativa()).isEqualTo(Duration.ofSeconds(278));
        assertThat(bloco.duracao()).isEqualTo(Duration.ofMinutes(5));
        assertThat(bloco.itens().getFirst())
                .isEqualTo(new MontarBloco.Item(
                        "sentar-e-levantar",
                        "Sentar e levantar da cadeira",
                        "Pernas",
                        "10 repetições",
                        Duration.ofSeconds(45),
                        "Sente e levante da cadeira sem usar as mãos, com os pés na largura do quadril."));
    }
}
