package br.com.pausaativa.agenda.adapter.out.persistence;

import br.com.pausaativa.agenda.domain.BlocoDoMarco;
import br.com.pausaativa.agenda.domain.Categoria;
import br.com.pausaativa.agenda.domain.DuracaoDoBloco;
import br.com.pausaativa.agenda.domain.ExercicioProposto;
import br.com.pausaativa.agenda.domain.Marco;
import br.com.pausaativa.agenda.domain.StatusMarco;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "marco")
class MarcoEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jornada_id")
    private JornadaEntity jornada;

    @Enumerated(EnumType.STRING)
    private Categoria categoria;

    private int sequencia;

    @Enumerated(EnumType.STRING)
    private StatusMarco status;

    @Column(name = "segundos_trabalhados_previstos")
    private int segundosTrabalhadosPrevistos;

    @Column(name = "segundos_trabalhados_limite")
    private int segundosTrabalhadosLimite;

    /** Nulo no exercício. */
    @Column(name = "volume_ml", precision = 8, scale = 4)
    private BigDecimal volumeMl;

    private Instant previstoPara;
    private Instant disparadoEm;
    private Instant recebidoEm;
    private Instant respondidoEm;

    /** Duração e compensação do bloco: nulos na água e no exercício que ainda não disparou. */
    private Integer duracaoBlocoMin;

    private Boolean compensaAdiamento;

    /**
     * Os marcos chegam juntos com a jornada a cada tick; o lote carrega os itens de todos numa consulta
     * só, em vez de uma por marco.
     */
    @ElementCollection
    @CollectionTable(name = "item_do_bloco", joinColumns = @JoinColumn(name = "marco_id"))
    @OrderBy("ordem")
    @BatchSize(size = 24)
    private List<ItemDoBlocoEmbeddable> itens = new ArrayList<>();

    protected MarcoEntity() {}

    static MarcoEntity novo(Marco marco, JornadaEntity jornada) {
        MarcoEntity entidade = new MarcoEntity();
        entidade.id = marco.id();
        entidade.jornada = jornada;
        entidade.categoria = marco.categoria();
        entidade.sequencia = marco.sequencia();
        entidade.segundosTrabalhadosPrevistos =
                Math.toIntExact(marco.tempoTrabalhadoPrevisto().toSeconds());
        entidade.segundosTrabalhadosLimite =
                Math.toIntExact(marco.tempoTrabalhadoLimite().toSeconds());
        entidade.volumeMl = marco.volumeMl();
        entidade.atualizarCom(marco);
        return entidade;
    }

    /** Só o que muda depois de criado: status, horários e, no disparo do exercício, o bloco. */
    void atualizarCom(Marco marco) {
        status = marco.status();
        previstoPara = marco.previstoPara().orElse(null);
        disparadoEm = marco.disparadoEm().orElse(null);
        recebidoEm = marco.recebidoEm().orElse(null);
        respondidoEm = marco.respondidoEm().orElse(null);
        marco.bloco().ifPresent(this::gravarBloco);
    }

    /** O bloco não muda depois do disparo: os itens são gravados uma vez só. */
    private void gravarBloco(BlocoDoMarco bloco) {
        duracaoBlocoMin = bloco.duracao().minutos();
        compensaAdiamento = bloco.compensaAdiamento();
        if (itens.isEmpty()) {
            List<ExercicioProposto> exercicios = bloco.exercicios();
            for (int i = 0; i < exercicios.size(); i++) {
                itens.add(ItemDoBlocoEmbeddable.de(i + 1, exercicios.get(i)));
            }
        }
    }

    Marco paraDominio() {
        return Marco.reconstituir(
                id,
                categoria,
                sequencia,
                Duration.ofSeconds(segundosTrabalhadosPrevistos),
                Duration.ofSeconds(segundosTrabalhadosLimite),
                volumeMl,
                status,
                previstoPara,
                disparadoEm,
                recebidoEm,
                respondidoEm,
                null, // editadoEm: a coluna chega com a V5 (T3 da spec H4)
                bloco());
    }

    private BlocoDoMarco bloco() {
        if (duracaoBlocoMin == null) {
            return null;
        }
        return new BlocoDoMarco(
                new DuracaoDoBloco(duracaoBlocoMin),
                compensaAdiamento,
                itens.stream().map(ItemDoBlocoEmbeddable::paraDominio).toList());
    }

    UUID id() {
        return id;
    }
}
