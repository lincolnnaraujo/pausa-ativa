package br.com.pausaativa.treino.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.pausaativa.TesteDeIntegracao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;

/** Contrato HTTP do perfil (spec H3, seção 6) com a aplicação inteira e Postgres real. */
@TesteDeIntegracao
class PerfilApiTest {

    private static final String COMPLETO = """
            {"articulacoesPoupadas": ["PUNHO", "JOELHO"], "nivel": "INTERMEDIARIO",
             "equipamentos": ["HALTERES_2KG"], "aceitaChao": false}
            """;

    @Autowired
    WebApplicationContext contexto;

    @Autowired
    JdbcTemplate jdbc;

    MockMvcTester api;

    @BeforeEach
    void semPerfil() {
        jdbc.execute("truncate perfil_fisico cascade");
        api = MockMvcTester.from(contexto);
    }

    private MvcTestResult salvar(String corpo) {
        return api.put()
                .uri("/api/v1/perfil")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo)
                .exchange();
    }

    @Test
    void semPerfilResponde204() {
        assertThat(api.get().uri("/api/v1/perfil")).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void salvarDevolveOPerfilEOGetMostraOMesmo() throws Exception {
        MvcTestResult resultado = salvar(COMPLETO);

        assertThat(resultado).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
        var json = assertThat(resultado).bodyJson();
        // Articulações e equipamentos sempre na mesma ordem, a das constantes.
        json.extractingPath("$.articulacoesPoupadas").asArray().containsExactly("JOELHO", "PUNHO");
        json.extractingPath("$.nivel").isEqualTo("INTERMEDIARIO");
        json.extractingPath("$.equipamentos").asArray().containsExactly("HALTERES_2KG");
        json.extractingPath("$.aceitaChao").isEqualTo(false);
        assertThat(api.get().uri("/api/v1/perfil"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo(resultado.getResponse().getContentAsString());
    }

    @Test
    void salvarDeNovoSubstituiOPerfilInteiro() {
        salvar(COMPLETO);

        assertThat(salvar("""
                {"nivel": "INICIANTE", "aceitaChao": true}
                """)).hasStatusOk();

        var json = assertThat(api.get().uri("/api/v1/perfil")).bodyJson();
        json.extractingPath("$.articulacoesPoupadas").asArray().isEmpty();
        json.extractingPath("$.equipamentos").asArray().isEmpty();
        json.extractingPath("$.nivel").isEqualTo("INICIANTE");
        json.extractingPath("$.aceitaChao").isEqualTo(true);
    }

    @Test
    void semNivelOuSemAceitaChaoResponde400EmPortugues() {
        MvcTestResult semNivel = salvar("""
                {"aceitaChao": true}
                """);
        assertThat(semNivel).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(semNivel).bodyJson().extractingPath("$.detail").asString().startsWith("Informe o nível");

        MvcTestResult semChao = salvar("""
                {"nivel": "INICIANTE"}
                """);
        assertThat(semChao).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(semChao).bodyJson().extractingPath("$.detail").asString().contains("aceitaChao");
    }

    @Test
    void cadeiraEArticulacaoDesconhecidaSaoRecusadas() {
        assertThat(salvar("""
                {"nivel": "INICIANTE", "aceitaChao": true, "equipamentos": ["CADEIRA"]}
                """)).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(salvar("""
                {"nivel": "INICIANTE", "aceitaChao": true, "articulacoesPoupadas": ["QUADRIL"]}
                """)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.get().uri("/api/v1/perfil")).hasStatus(HttpStatus.NO_CONTENT);
    }
}
