package br.com.pausaativa.treino.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Cópias imutáveis de conjuntos de enum, sempre na ordem das constantes. */
final class Conjuntos {

    private Conjuntos() {}

    static <E extends Enum<E>> Set<E> copia(Collection<E> origem, Class<E> tipo) {
        EnumSet<E> copia = EnumSet.noneOf(tipo);
        copia.addAll(origem);
        return Collections.unmodifiableSet(copia);
    }
}
