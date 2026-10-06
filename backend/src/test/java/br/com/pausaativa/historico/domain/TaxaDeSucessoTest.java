package br.com.pausaativa.historico.domain;

import static br.com.pausaativa.historico.domain.Situacao.CONCLUIDO;
import static br.com.pausaativa.historico.domain.Situacao.EM_ABERTO;
import static br.com.pausaativa.historico.domain.Situacao.FALHA;
import static br.com.pausaativa.historico.domain.Situacao.NAO_CONCLUIDO;
import static br.com.pausaativa.historico.domain.Situacao.NAO_ENTREGUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Taxa de sucesso: a fórmula do épico, o truncamento e a meta exata (spec H4, seção 3.1). */
class TaxaDeSucessoTest {

    @Nested
    class Taxa {

        @Test
        void cenario1DozeConcluidosEDuasFalhasDao857() {
            TaxaDeSucesso taxa = new TaxaDeSucesso(12, 2);

            assertThat(taxa.percentual()).hasToString("85.7");
            assertThat(taxa.metaAtingida()).isTrue();
        }

        @Test
        void abaixoDaMeta() {
            TaxaDeSucesso taxa = new TaxaDeSucesso(5, 2);

            assertThat(taxa.percentual()).hasToString("71.4");
            assertThat(taxa.metaAtingida()).isFalse();
        }

        @Test
        void truncaEmVezDeArredondar() {
            assertThat(new TaxaDeSucesso(15, 1).percentual()).hasToString("93.7"); // 93,75
            assertThat(new TaxaDeSucesso(1999, 500).percentual()).hasToString("79.9"); // 79,99…
        }

        @Test
        void naoMostra80NumaTaxaAbaixoDaMeta() {
            TaxaDeSucesso taxa = new TaxaDeSucesso(3999, 1001); // 79,98%: arredondado, daria 80,0

            assertThat(taxa.percentual()).hasToString("79.9");
            assertThat(taxa.metaAtingida()).isFalse();
        }

        @Test
        void oitentaPorCentoExatosAtingemAMeta() {
            assertThat(new TaxaDeSucesso(4, 1).metaAtingida()).isTrue();
            assertThat(new TaxaDeSucesso(4, 1).percentual()).hasToString("80.0");
            assertThat(new TaxaDeSucesso(12, 3).metaAtingida()).isTrue();
        }

        @Test
        void extremos() {
            assertThat(new TaxaDeSucesso(3, 0).percentual()).hasToString("100.0");
            assertThat(new TaxaDeSucesso(0, 3).percentual()).hasToString("0.0");
            assertThat(new TaxaDeSucesso(0, 3).metaAtingida()).isFalse();
        }

        @Test
        void semRespondidosNaoHaTaxa() {
            assertThatThrownBy(() -> new TaxaDeSucesso(0, 0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new TaxaDeSucesso(-1, 2)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Contagem {

        @Test
        void cenario1NaoEntreguesFicamForaDaTaxa() {
            ContagemPorSituacao contagem =
                    ContagemPorSituacao.VAZIA.com(CONCLUIDO, 12).com(FALHA, 2).com(NAO_ENTREGUE, 2);

            assertThat(contagem).isEqualTo(new ContagemPorSituacao(12, 2, 2, 0, 0));
            assertThat(contagem.taxa()).contains(new TaxaDeSucesso(12, 2));
        }

        @Test
        void naoConcluidoEEmAbertoTambemFicamFora() {
            ContagemPorSituacao contagem = ContagemPorSituacao.VAZIA
                    .com(CONCLUIDO, 3)
                    .com(FALHA, 1)
                    .com(NAO_CONCLUIDO, 4)
                    .com(EM_ABERTO, 8);

            assertThat(contagem).isEqualTo(new ContagemPorSituacao(3, 1, 0, 4, 8));
            assertThat(contagem.taxa()).contains(new TaxaDeSucesso(3, 1));
        }

        @Test
        void semConcluidoNemFalhaNaoTemTaxa() {
            assertThat(new ContagemPorSituacao(0, 0, 2, 14, 0).taxa()).isEmpty();
            assertThat(ContagemPorSituacao.VAZIA.taxa()).isEmpty();
        }

        @Test
        void somaSituacaoASituacao() {
            assertThat(new ContagemPorSituacao(1, 2, 3, 4, 5).mais(new ContagemPorSituacao(10, 20, 30, 40, 50)))
                    .isEqualTo(new ContagemPorSituacao(11, 22, 33, 44, 55));
        }

        @Test
        void contagemNegativaERecusada() {
            assertThatThrownBy(() -> new ContagemPorSituacao(0, 0, 0, -1, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
