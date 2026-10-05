package br.com.pausaativa.agenda.application;

import br.com.pausaativa.agenda.application.port.out.MontadorDeBlocos;
import br.com.pausaativa.agenda.application.port.out.MontadorDeBlocos.PedidoDeBloco;
import br.com.pausaativa.agenda.domain.Avanco;
import br.com.pausaativa.agenda.domain.Jornada;
import br.com.pausaativa.agenda.domain.Marco;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Avança a jornada e entrega a cada exercício disparado o bloco montado pelo Treino, na mesma transação e
 * antes de gravar (spec H3, seção 4): nenhum exercício pendente fica sem bloco. Todo caso de uso que põe a
 * jornada em dia passa por aqui.
 */
@Component
class AvancoDaJornada {

    private final MontadorDeBlocos montador;

    AvancoDaJornada(MontadorDeBlocos montador) {
        this.montador = montador;
    }

    Avanco avancar(Jornada jornada, Instant agora) {
        Avanco avanco = jornada.avancar(agora);
        for (Marco marco : avanco.disparados()) {
            marco.bloco()
                    .ifPresent(bloco -> jornada.atribuirBloco(
                            marco.id(),
                            montador.montar(new PedidoDeBloco(
                                    bloco.duracao(), marco.sequencia(), jornada.exerciciosPropostosNoDia()))));
        }
        return avanco;
    }
}
