package br.com.pausaativa.historico.adapter.in.web;

import br.com.pausaativa.historico.domain.Categoria;
import br.com.pausaativa.historico.domain.ContagemPorSituacao;
import br.com.pausaativa.historico.domain.DiaDoPeriodo;
import br.com.pausaativa.historico.domain.EstadoDaJornada;
import br.com.pausaativa.historico.domain.ResumoDaCategoria;
import br.com.pausaativa.historico.domain.ResumoDoPeriodo;
import br.com.pausaativa.historico.domain.TaxaDeSucesso;
import br.com.pausaativa.historico.domain.TipoDePeriodo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Schema(
        name = "Historico",
        description = "Uma visão do histórico: o resumo de cada categoria e todos os dias do período.",
        requiredProperties = {"periodo", "inicio", "fim", "categorias", "dias"})
record HistoricoResposta(
        TipoDePeriodo periodo,
        @Schema(description = "Primeiro dia do período") LocalDate inicio,

        @Schema(description = "Último dia do período, inclusive")
        LocalDate fim,

        @Schema(description = "Água e exercício, nessa ordem")
        List<CategoriaResposta> categorias,

        @Schema(description = "Todos os dias do período, em ordem, com ou sem jornada")
        List<DiaResposta> dias) {

    static HistoricoResposta de(ResumoDoPeriodo resumo) {
        return new HistoricoResposta(
                resumo.periodo().tipo(),
                resumo.periodo().inicio(),
                resumo.periodo().fim(),
                resumo.categorias().stream().map(CategoriaResposta::de).toList(),
                resumo.dias().stream().map(DiaResposta::de).toList());
    }

    private static BigDecimal percentualDa(Optional<TaxaDeSucesso> taxa) {
        return taxa.map(TaxaDeSucesso::percentual).orElse(null);
    }

    private static Boolean metaAtingidaPela(Optional<TaxaDeSucesso> taxa) {
        return taxa.map(TaxaDeSucesso::metaAtingida).orElse(null);
    }

    @Schema(
            name = "ResumoDaCategoria",
            description = "Uma categoria no período: situações somadas, taxa do período e dias na meta.",
            requiredProperties = {
                "categoria",
                "concluidos",
                "falhas",
                "naoEntregues",
                "naoConcluidos",
                "emAberto",
                "taxa",
                "metaAtingida",
                "diasComDados",
                "diasNaMeta"
            })
    record CategoriaResposta(
            Categoria categoria,
            int concluidos,
            int falhas,
            int naoEntregues,
            int naoConcluidos,

            @Schema(description = "Agendados, pendentes e adiados sem destino, só na jornada em andamento")
            int emAberto,

            @Schema(
                    description = "Concluídos ÷ (concluídos + falhas) do período, em %, com uma casa, truncado."
                            + " Nula sem nenhum concluído nem falha.",
                    types = {"number", "null"},
                    example = "85.7")
            BigDecimal taxa,

            @Schema(
                    description = "Se a taxa chegou a 80%, pela fração exata. Nula sem dados.",
                    types = {"boolean", "null"})
            Boolean metaAtingida,

            @Schema(description = "Dias com pelo menos um concluído ou uma falha na categoria", example = "4")
            int diasComDados,

            @Schema(description = "Desses dias, os que tiveram taxa de pelo menos 80%", example = "3")
            int diasNaMeta) {

        static CategoriaResposta de(ResumoDaCategoria resumo) {
            ContagemPorSituacao total = resumo.total();
            return new CategoriaResposta(
                    resumo.categoria(),
                    total.concluidos(),
                    total.falhas(),
                    total.naoEntregues(),
                    total.naoConcluidos(),
                    total.emAberto(),
                    percentualDa(resumo.taxa()),
                    metaAtingidaPela(resumo.taxa()),
                    resumo.diasComDados(),
                    resumo.diasNaMeta());
        }
    }

    @Schema(
            name = "DiaDoHistorico",
            description = "Um dia do período. Sem jornada, não tem categorias e não conta como falha.",
            requiredProperties = {"data", "jornada", "futuro", "categorias"})
    record DiaResposta(
            LocalDate data,

            @Schema(
                    description = "Estado da jornada do dia. Nulo sem jornada.",
                    types = {"string", "null"})
            EstadoDaJornada jornada,

            @Schema(description = "Dia depois de hoje, dentro da semana ou do mês corrente")
            boolean futuro,

            @Schema(description = "Água e exercício, nessa ordem; vazia sem jornada")
            List<ContagemResposta> categorias) {

        static DiaResposta de(DiaDoPeriodo dia) {
            List<ContagemResposta> categorias = dia.jornada().isEmpty()
                    ? List.of()
                    : Arrays.stream(Categoria.values())
                            .map(categoria -> ContagemResposta.de(categoria, dia.contagem(categoria)))
                            .toList();
            return new DiaResposta(dia.data(), dia.jornada().orElse(null), dia.futuro(), categorias);
        }
    }

    @Schema(
            name = "ContagemDoDia",
            description = "Uma categoria num dia: situações, taxa e meta.",
            requiredProperties = {
                "categoria",
                "concluidos",
                "falhas",
                "naoEntregues",
                "naoConcluidos",
                "emAberto",
                "taxa",
                "metaAtingida"
            })
    record ContagemResposta(
            Categoria categoria,
            int concluidos,
            int falhas,
            int naoEntregues,
            int naoConcluidos,
            int emAberto,

            @Schema(
                    description = "Taxa do dia, em %, truncada em uma casa. Nula sem dados.",
                    types = {"number", "null"},
                    example = "85.7")
            BigDecimal taxa,

            @Schema(
                    description = "Se o dia chegou a 80%. Nula sem dados.",
                    types = {"boolean", "null"})
            Boolean metaAtingida) {

        static ContagemResposta de(Categoria categoria, ContagemPorSituacao contagem) {
            return new ContagemResposta(
                    categoria,
                    contagem.concluidos(),
                    contagem.falhas(),
                    contagem.naoEntregues(),
                    contagem.naoConcluidos(),
                    contagem.emAberto(),
                    percentualDa(contagem.taxa()),
                    metaAtingidaPela(contagem.taxa()));
        }
    }
}
