package br.com.pausaativa.historico.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Uma categoria num período: o total por situação, a taxa do período e os dias na meta (spec H4,
 * seção 3.2).
 *
 * @param taxa a soma dos marcos do período, e não a média das taxas de cada dia (decisão D3)
 * @param diasComDados dias com pelo menos um concluído ou uma falha na categoria
 * @param diasNaMeta desses dias, os que tiveram taxa de pelo menos 80%
 */
public record ResumoDaCategoria(
        Categoria categoria,
        ContagemPorSituacao total,
        Optional<TaxaDeSucesso> taxa,
        int diasComDados,
        int diasNaMeta) {

    public ResumoDaCategoria {
        Objects.requireNonNull(categoria, "categoria");
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(taxa, "taxa");
    }

    static ResumoDaCategoria de(Categoria categoria, List<DiaDoPeriodo> dias) {
        ContagemPorSituacao total = ContagemPorSituacao.VAZIA;
        int diasComDados = 0;
        int diasNaMeta = 0;
        for (DiaDoPeriodo dia : dias) {
            ContagemPorSituacao contagem = dia.contagem(categoria);
            total = total.mais(contagem);
            Optional<TaxaDeSucesso> taxaDoDia = contagem.taxa();
            if (taxaDoDia.isPresent()) {
                diasComDados++;
                if (taxaDoDia.get().metaAtingida()) {
                    diasNaMeta++;
                }
            }
        }
        return new ResumoDaCategoria(categoria, total, total.taxa(), diasComDados, diasNaMeta);
    }
}
