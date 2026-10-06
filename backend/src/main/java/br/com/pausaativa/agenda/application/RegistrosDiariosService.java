package br.com.pausaativa.agenda.application;

import br.com.pausaativa.agenda.application.port.in.ConsultarRegistrosDiarios;
import br.com.pausaativa.agenda.application.port.out.ContagemDoDia;
import br.com.pausaativa.agenda.application.port.out.JornadaRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Entrega ao Histórico as contagens feitas no banco, agrupadas por dia (spec H4, seção 4). */
@Service
class RegistrosDiariosService implements ConsultarRegistrosDiarios {

    private final JornadaRepository repositorio;

    RegistrosDiariosService(JornadaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegistroDiario> entre(LocalDate de, LocalDate ate) {
        Map<LocalDate, List<ContagemDoDia>> porDia = new LinkedHashMap<>();
        for (ContagemDoDia linha : repositorio.contarPorDia(de, ate)) {
            porDia.computeIfAbsent(linha.data(), dia -> new ArrayList<>()).add(linha);
        }
        return porDia.values().stream().map(RegistrosDiariosService::registro).toList();
    }

    /** Uma jornada por dia: todas as linhas do dia têm o mesmo status de jornada. */
    private static RegistroDiario registro(List<ContagemDoDia> linhas) {
        ContagemDoDia primeira = linhas.getFirst();
        return new RegistroDiario(
                primeira.data(),
                primeira.jornada().name(),
                linhas.stream()
                        .map(linha -> new Contagem(
                                linha.categoria().name(), linha.status().name(), linha.quantidade()))
                        .toList());
    }
}
