package br.com.pausaativa.historico.application;

import br.com.pausaativa.historico.application.port.in.ConsultarHistorico;
import br.com.pausaativa.historico.application.port.out.RegistrosDiarios;
import br.com.pausaativa.historico.domain.Periodo;
import br.com.pausaativa.historico.domain.ResumoDoPeriodo;
import br.com.pausaativa.historico.domain.TipoDePeriodo;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/** Monta a visão pedida com as contagens da Agenda; hoje vem do {@link Clock} da aplicação. */
@Service
class HistoricoService implements ConsultarHistorico {

    private final RegistrosDiarios registros;
    private final Clock clock;

    HistoricoService(RegistrosDiarios registros, Clock clock) {
        this.registros = registros;
        this.clock = clock;
    }

    @Override
    public ResumoDoPeriodo consultar(TipoDePeriodo tipo, LocalDate data) {
        LocalDate hoje = LocalDate.now(clock);
        Periodo periodo = Periodo.contendo(tipo, data, hoje);
        LocalDate ate = periodo.fim().isAfter(hoje) ? hoje : periodo.fim();
        return ResumoDoPeriodo.de(periodo, registros.entre(periodo.inicio(), ate), hoje);
    }
}
