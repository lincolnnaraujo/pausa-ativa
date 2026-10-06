package br.com.pausaativa.historico.domain;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/** O que a Agenda conta de um dia com jornada: o estado dela e as situações por categoria. */
public record RegistroDoDia(LocalDate data, EstadoDaJornada jornada, Map<Categoria, ContagemPorSituacao> contagens) {

    public RegistroDoDia {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(jornada, "jornada");
        contagens = Map.copyOf(contagens);
    }

    /** Vazia na categoria sem lembretes, como o exercício de uma jornada da v0.2.0. */
    public ContagemPorSituacao contagem(Categoria categoria) {
        return contagens.getOrDefault(categoria, ContagemPorSituacao.VAZIA);
    }
}
