package br.com.pausaativa.treino.application;

import br.com.pausaativa.treino.application.port.in.MontarBloco;
import br.com.pausaativa.treino.application.port.out.CatalogoRepository;
import br.com.pausaativa.treino.application.port.out.PerfilRepository;
import br.com.pausaativa.treino.domain.BlocoDeExercicio;
import br.com.pausaativa.treino.domain.ItemDoBloco;
import br.com.pausaativa.treino.domain.PedidoDeBloco;
import br.com.pausaativa.treino.domain.PerfilFisico;
import br.com.pausaativa.treino.domain.SelecaoDeBloco;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monta o bloco com o perfil atual e o catálogo do banco. Roda dentro da transação de quem dispara o marco. */
@Service
class MontagemDeBlocoService implements MontarBloco {

    private static final Logger log = LoggerFactory.getLogger(MontagemDeBlocoService.class);

    private final PerfilRepository perfis;
    private final CatalogoRepository catalogo;

    MontagemDeBlocoService(PerfilRepository perfis, CatalogoRepository catalogo) {
        this.perfis = perfis;
        this.catalogo = catalogo;
    }

    @Override
    @Transactional(readOnly = true)
    public BlocoMontado montar(Pedido pedido) {
        PerfilFisico perfil = perfis.buscar()
                .orElseThrow(() -> new IllegalStateException("O bloco só é montado com o perfil físico preenchido"));
        BlocoDeExercicio bloco = new SelecaoDeBloco(catalogo.todos())
                .montar(perfil, new PedidoDeBloco(pedido.duracao(), pedido.numeroDoMarco(), pedido.usadosNoDia()));

        BlocoMontado montado = new BlocoMontado(
                bloco.duracao(),
                bloco.estimativa(),
                bloco.itens().stream().map(MontagemDeBlocoService::item).toList());
        log.atInfo()
                .addKeyValue("numeroDoMarco", pedido.numeroDoMarco())
                .addKeyValue("duracaoMin", pedido.duracao().toMinutes())
                .addKeyValue("estimativaS", montado.estimativa().toSeconds())
                .addKeyValue(
                        "exercicios", montado.itens().stream().map(Item::codigo).toList())
                .log("Bloco de exercício montado");
        return montado;
    }

    private static Item item(ItemDoBloco item) {
        return new Item(
                item.exercicio().codigo(),
                item.exercicio().nome(),
                item.exercicio().grupo().nome(),
                item.quantidade().texto(),
                item.estimativa(),
                item.exercicio().instrucao());
    }
}
