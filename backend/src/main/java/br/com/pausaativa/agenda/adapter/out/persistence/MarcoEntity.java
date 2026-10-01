package br.com.pausaativa.agenda.adapter.out.persistence;

import br.com.pausaativa.agenda.domain.Categoria;
import br.com.pausaativa.agenda.domain.Marco;
import br.com.pausaativa.agenda.domain.StatusMarco;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

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

    @Column(name = "volume_ml", precision = 8, scale = 4)
    private BigDecimal volumeMl;

    private Instant previstoPara;
    private Instant disparadoEm;
    private Instant recebidoEm;
    private Instant respondidoEm;

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

    /** Só o que muda depois de criado: status e horários. */
    void atualizarCom(Marco marco) {
        status = marco.status();
        previstoPara = marco.previstoPara().orElse(null);
        disparadoEm = marco.disparadoEm().orElse(null);
        recebidoEm = marco.recebidoEm().orElse(null);
        respondidoEm = marco.respondidoEm().orElse(null);
    }

    Marco paraDominio() {
        return Marco.reconstituir(
                id,
                categoria,
                sequencia,
                Duration.ofSeconds(segundosTrabalhadosPrevistos),
                Duration.ofSeconds(segundosTrabalhadosLimite),
                // O banco devolve 187.5000; o domínio e a API trabalham com 187.5.
                volumeMl.stripTrailingZeros(),
                status,
                previstoPara,
                disparadoEm,
                recebidoEm,
                respondidoEm);
    }

    UUID id() {
        return id;
    }
}
