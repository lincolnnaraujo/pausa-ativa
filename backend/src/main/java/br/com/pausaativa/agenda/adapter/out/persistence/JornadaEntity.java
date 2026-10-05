package br.com.pausaativa.agenda.adapter.out.persistence;

import br.com.pausaativa.agenda.domain.DuracaoDoBloco;
import br.com.pausaativa.agenda.domain.Jornada;
import br.com.pausaativa.agenda.domain.Marco;
import br.com.pausaativa.agenda.domain.MetaDeAgua;
import br.com.pausaativa.agenda.domain.StatusJornada;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Entity
@Table(name = "jornada")
class JornadaEntity {

    @Id
    private UUID id;

    /** Nulo só antes do primeiro insert: é o que diz ao Spring Data que a entidade é nova. */
    @Version
    private Long versao;

    private LocalDate dataReferencia;

    @Enumerated(EnumType.STRING)
    private StatusJornada status;

    private int metaAguaMl;
    private Instant iniciadaEm;
    private Instant finalizadaEm;

    @ElementCollection
    @CollectionTable(name = "pausa", joinColumns = @JoinColumn(name = "jornada_id"))
    @OrderBy("inicio")
    private List<PausaEmbeddable> pausas = new ArrayList<>();

    @OneToMany(mappedBy = "jornada", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("categoria, sequencia")
    private List<MarcoEntity> marcos = new ArrayList<>();

    protected JornadaEntity() {}

    static JornadaEntity nova(Jornada jornada) {
        JornadaEntity entidade = new JornadaEntity();
        entidade.id = jornada.id();
        entidade.dataReferencia = jornada.dataReferencia();
        entidade.metaAguaMl = jornada.meta().mililitros();
        entidade.iniciadaEm = jornada.iniciadaEm();
        jornada.marcos().forEach(marco -> entidade.marcos.add(MarcoEntity.novo(marco, entidade)));
        entidade.atualizarCom(jornada);
        return entidade;
    }

    /** Copia o que a jornada pode mudar depois de criada: status, fim, pausas e marcos. */
    void atualizarCom(Jornada jornada) {
        status = jornada.status();
        finalizadaEm = jornada.finalizadaEm().orElse(null);

        List<PausaEmbeddable> pausasAtuais =
                jornada.pausas().stream().map(PausaEmbeddable::de).toList();
        if (!pausas.equals(pausasAtuais)) {
            // A coleção é regravada inteira; só vale a pena quando algo mudou.
            pausas.clear();
            pausas.addAll(pausasAtuais);
        }

        Map<UUID, MarcoEntity> porId = marcos.stream().collect(Collectors.toMap(MarcoEntity::id, Function.identity()));
        for (Marco marco : jornada.marcos()) {
            porId.get(marco.id()).atualizarCom(marco);
        }
    }

    Jornada paraDominio() {
        return Jornada.reconstituir(
                id,
                dataReferencia,
                new MetaDeAgua(metaAguaMl),
                DuracaoDoBloco.PADRAO,
                iniciadaEm,
                status,
                finalizadaEm,
                pausas.stream().map(PausaEmbeddable::paraDominio).toList(),
                marcos.stream().map(MarcoEntity::paraDominio).toList());
    }
}
