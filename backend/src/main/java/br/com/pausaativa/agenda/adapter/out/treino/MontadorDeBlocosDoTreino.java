package br.com.pausaativa.agenda.adapter.out.treino;

import br.com.pausaativa.agenda.application.port.out.MontadorDeBlocos;
import br.com.pausaativa.agenda.domain.ExercicioProposto;
import br.com.pausaativa.treino.application.port.in.ConsultarPerfil;
import br.com.pausaativa.treino.application.port.in.MontarBloco;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Implementa a porta da Agenda com as portas de entrada do Treino (regra R4). É a única peça da Agenda
 * que conhece o Treino; o Treino não conhece a Agenda (R5).
 */
@Component
class MontadorDeBlocosDoTreino implements MontadorDeBlocos {

    private final MontarBloco montarBloco;
    private final ConsultarPerfil consultarPerfil;

    MontadorDeBlocosDoTreino(MontarBloco montarBloco, ConsultarPerfil consultarPerfil) {
        this.montarBloco = montarBloco;
        this.consultarPerfil = consultarPerfil;
    }

    @Override
    public boolean perfilPreenchido() {
        return consultarPerfil.preenchido();
    }

    @Override
    public List<ExercicioProposto> montar(PedidoDeBloco pedido) {
        return propostos(montarBloco.montar(paraOTreino(pedido)));
    }

    @Override
    public List<ExercicioProposto> montarDeExemplo(PedidoDeBloco pedido) {
        return propostos(montarBloco.montarDeExemplo(paraOTreino(pedido)));
    }

    private static MontarBloco.Pedido paraOTreino(PedidoDeBloco pedido) {
        return new MontarBloco.Pedido(pedido.duracao().duracao(), pedido.numeroDoMarco(), pedido.propostosNoDia());
    }

    private static List<ExercicioProposto> propostos(MontarBloco.BlocoMontado bloco) {
        return bloco.itens().stream()
                .map(item -> new ExercicioProposto(
                        item.codigo(),
                        item.exercicio(),
                        item.grupo(),
                        item.quantidade(),
                        item.estimativa(),
                        item.instrucao()))
                .toList();
    }
}
