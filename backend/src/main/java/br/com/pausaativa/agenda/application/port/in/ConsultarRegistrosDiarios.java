package br.com.pausaativa.agenda.application.port.in;

import java.time.LocalDate;
import java.util.List;

/**
 * As contagens por dia que o Histórico lê (spec H4, seção 4). Os tipos são deste pacote e só do JDK,
 * porque o Histórico só enxerga o {@code application.port.in} da Agenda (regra R4).
 */
public interface ConsultarRegistrosDiarios {

    /** Os dias com jornada de {@code de} a {@code ate}, inclusive, em ordem de data. */
    List<RegistroDiario> entre(LocalDate de, LocalDate ate);

    /**
     * @param jornada o status da jornada do dia: {@code EM_ANDAMENTO}, {@code PAUSADA}, {@code FINALIZADA}
     *     ou {@code ENCERRADA_AUTOMATICAMENTE}
     */
    record RegistroDiario(LocalDate data, String jornada, List<Contagem> contagens) {

        public RegistroDiario {
            contagens = List.copyOf(contagens);
        }
    }

    /**
     * @param categoria {@code HIDRATACAO} ou {@code EXERCICIO}
     * @param status a situação do marco, como no contrato da API: {@code AGENDADO}, {@code PENDENTE},
     *     {@code CONCLUIDO}, {@code FALHA}, {@code NAO_ENTREGUE}, {@code NAO_CONCLUIDO} ou {@code ADIADO}
     */
    record Contagem(String categoria, String status, long quantidade) {}
}
