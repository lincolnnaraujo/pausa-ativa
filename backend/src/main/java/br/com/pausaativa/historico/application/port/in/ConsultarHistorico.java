package br.com.pausaativa.historico.application.port.in;

import br.com.pausaativa.historico.domain.ResumoDoPeriodo;
import br.com.pausaativa.historico.domain.TipoDePeriodo;
import java.time.LocalDate;

/** Uma visão do histórico: o dia, a semana ou o mês que contém a data (spec H4, seção 3.2). */
public interface ConsultarHistorico {

    /** @throws br.com.pausaativa.historico.domain.DataFuturaException se a data é depois de hoje */
    ResumoDoPeriodo consultar(TipoDePeriodo tipo, LocalDate data);
}
