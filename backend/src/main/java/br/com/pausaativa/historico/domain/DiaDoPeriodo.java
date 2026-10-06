package br.com.pausaativa.historico.domain;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Um dia de um período do histórico. Sem jornada, ele não tem contagens e não conta como falha; depois de
 * hoje, é {@code futuro} (spec H4, seção 3.2).
 */
public record DiaDoPeriodo(
        LocalDate data,
        Optional<EstadoDaJornada> jornada,
        boolean futuro,
        Map<Categoria, ContagemPorSituacao> contagens) {

    public DiaDoPeriodo {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(jornada, "jornada");
        contagens = Map.copyOf(contagens);
    }

    static DiaDoPeriodo de(RegistroDoDia registro) {
        return new DiaDoPeriodo(registro.data(), Optional.of(registro.jornada()), false, registro.contagens());
    }

    static DiaDoPeriodo semJornada(LocalDate data, boolean futuro) {
        return new DiaDoPeriodo(data, Optional.empty(), futuro, Map.of());
    }

    public ContagemPorSituacao contagem(Categoria categoria) {
        return contagens.getOrDefault(categoria, ContagemPorSituacao.VAZIA);
    }
}
