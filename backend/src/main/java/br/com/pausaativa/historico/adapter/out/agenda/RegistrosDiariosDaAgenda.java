package br.com.pausaativa.historico.adapter.out.agenda;

import br.com.pausaativa.agenda.application.port.in.ConsultarRegistrosDiarios;
import br.com.pausaativa.agenda.application.port.in.ConsultarRegistrosDiarios.Contagem;
import br.com.pausaativa.agenda.application.port.in.ConsultarRegistrosDiarios.RegistroDiario;
import br.com.pausaativa.historico.application.port.out.RegistrosDiarios;
import br.com.pausaativa.historico.domain.Categoria;
import br.com.pausaativa.historico.domain.ContagemPorSituacao;
import br.com.pausaativa.historico.domain.EstadoDaJornada;
import br.com.pausaativa.historico.domain.RegistroDoDia;
import br.com.pausaativa.historico.domain.Situacao;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Traduz as contagens da Agenda para o domínio do Histórico. É a única peça do Histórico que conhece a
 * Agenda (spec H4, seção 4).
 */
@Component
class RegistrosDiariosDaAgenda implements RegistrosDiarios {

    private final ConsultarRegistrosDiarios agenda;

    RegistrosDiariosDaAgenda(ConsultarRegistrosDiarios agenda) {
        this.agenda = agenda;
    }

    @Override
    public List<RegistroDoDia> entre(LocalDate de, LocalDate ate) {
        return agenda.entre(de, ate).stream()
                .map(RegistrosDiariosDaAgenda::registro)
                .toList();
    }

    private static RegistroDoDia registro(RegistroDiario dia) {
        Map<Categoria, ContagemPorSituacao> contagens = new EnumMap<>(Categoria.class);
        for (Contagem contagem : dia.contagens()) {
            contagens.merge(
                    Categoria.valueOf(contagem.categoria()),
                    ContagemPorSituacao.VAZIA.com(situacao(contagem.status()), Math.toIntExact(contagem.quantidade())),
                    ContagemPorSituacao::mais);
        }
        return new RegistroDoDia(dia.data(), EstadoDaJornada.valueOf(dia.jornada()), contagens);
    }

    /** Agendado, pendente e adiado sem destino ainda pedem resposta: ficam em aberto (spec H4, seção 3.1). */
    static Situacao situacao(String status) {
        return switch (status) {
            case "CONCLUIDO" -> Situacao.CONCLUIDO;
            case "FALHA" -> Situacao.FALHA;
            case "NAO_ENTREGUE" -> Situacao.NAO_ENTREGUE;
            case "NAO_CONCLUIDO" -> Situacao.NAO_CONCLUIDO;
            case "AGENDADO", "PENDENTE", "ADIADO" -> Situacao.EM_ABERTO;
            default -> throw new IllegalStateException("Situação de marco desconhecida: " + status);
        };
    }
}
