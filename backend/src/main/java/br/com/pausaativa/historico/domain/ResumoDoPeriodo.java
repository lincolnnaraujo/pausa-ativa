package br.com.pausaativa.historico.domain;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Uma visão do histórico: o resumo de cada categoria e todos os dias do período, com ou sem jornada (spec
 * H4, seções 3.1 e 3.2).
 */
public record ResumoDoPeriodo(Periodo periodo, List<ResumoDaCategoria> categorias, List<DiaDoPeriodo> dias) {

    public ResumoDoPeriodo {
        Objects.requireNonNull(periodo, "periodo");
        categorias = List.copyOf(categorias);
        dias = List.copyOf(dias);
    }

    /**
     * Monta o resumo a partir dos dias com jornada. Os demais dias do período entram sem jornada; os
     * posteriores a hoje, como futuros.
     *
     * @throws IllegalArgumentException se um registro está fora do período, depois de hoje ou repetido
     */
    public static ResumoDoPeriodo de(Periodo periodo, Collection<RegistroDoDia> registros, LocalDate hoje) {
        Map<LocalDate, RegistroDoDia> porData = new HashMap<>();
        for (RegistroDoDia registro : registros) {
            if (!periodo.contem(registro.data()) || registro.data().isAfter(hoje)) {
                throw new IllegalArgumentException("Registro de %s fora do período %s a %s, até %s"
                        .formatted(registro.data(), periodo.inicio(), periodo.fim(), hoje));
            }
            if (porData.putIfAbsent(registro.data(), registro) != null) {
                throw new IllegalArgumentException("Dois registros para o dia " + registro.data());
            }
        }
        List<DiaDoPeriodo> dias = periodo.dias().stream()
                .map(data -> porData.containsKey(data)
                        ? DiaDoPeriodo.de(porData.get(data))
                        : DiaDoPeriodo.semJornada(data, data.isAfter(hoje)))
                .toList();
        List<ResumoDaCategoria> categorias = Arrays.stream(Categoria.values())
                .map(categoria -> ResumoDaCategoria.de(categoria, dias))
                .toList();
        return new ResumoDoPeriodo(periodo, categorias, dias);
    }

    /** Nenhum dia do período teve jornada: a tela mostra o estado vazio (Cenário 6 da H4). */
    public boolean semJornada() {
        return dias.stream().allMatch(dia -> dia.jornada().isEmpty());
    }

    public ResumoDaCategoria categoria(Categoria categoria) {
        return categorias.stream()
                .filter(resumo -> resumo.categoria() == categoria)
                .findFirst()
                .orElseThrow();
    }
}
