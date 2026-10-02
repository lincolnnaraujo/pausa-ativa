package br.com.pausaativa.treino.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Exercício do catálogo (épico, seção "Treino"). O catálogo entra no banco por migração e não muda pela
 * tela.
 *
 * <p>Um exercício de <b>reserva</b> serve para qualquer perfil: sem restrição nem equipamento, em pé e
 * com quantidade nos dois níveis. É ele que completa o bloco quando o perfil deixa poucos elegíveis
 * (Cenário 7), e por isso o construtor recusa uma reserva que não cumpra isso.
 */
public final class Exercicio {

    private final String codigo;
    private final String nome;
    private final GrupoMuscular grupo;
    private final int ordem;
    private final String instrucao;
    private final FormaDeQuantidade forma;
    private final Integer quantidadeIniciante;
    private final int quantidadeIntermediario;
    private final Equipamento equipamento;
    private final boolean noChao;
    private final Set<Articulacao> restricoes;
    private final boolean reserva;

    /**
     * @param ordem posição no catálogo; desempata a escolha dentro de um grupo
     * @param quantidadeIniciante nulo quando o exercício é só do intermediário
     * @param equipamento nulo quando não exige nenhum
     */
    public Exercicio(
            String codigo,
            String nome,
            GrupoMuscular grupo,
            int ordem,
            String instrucao,
            FormaDeQuantidade forma,
            Integer quantidadeIniciante,
            int quantidadeIntermediario,
            Equipamento equipamento,
            boolean noChao,
            Set<Articulacao> restricoes,
            boolean reserva) {
        this.codigo = obrigatorio(codigo, "codigo");
        this.nome = obrigatorio(nome, "nome");
        this.grupo = Objects.requireNonNull(grupo, "grupo");
        this.ordem = ordem;
        this.instrucao = obrigatorio(instrucao, "instrucao");
        this.forma = Objects.requireNonNull(forma, "forma");
        this.quantidadeIniciante = quantidadeIniciante;
        this.quantidadeIntermediario = quantidadeIntermediario;
        this.equipamento = equipamento;
        this.noChao = noChao;
        this.restricoes = Conjuntos.copia(restricoes, Articulacao.class);
        this.reserva = reserva;
        // Valida as quantidades já no construtor, e não só na hora de montar o bloco.
        quantidadePara(Nivel.INICIANTE);
        quantidadePara(Nivel.INTERMEDIARIO);
        if (reserva && (!this.restricoes.isEmpty() || equipamento != null || noChao || quantidadeIniciante == null)) {
            throw new IllegalArgumentException("A reserva precisa servir para qualquer perfil: " + codigo);
        }
    }

    private static String obrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Campo obrigatório do exercício: " + campo);
        }
        return valor;
    }

    /** Vazio quando o exercício não é do nível (o afundo e a flexão com apoio só têm intermediário). */
    public Optional<Quantidade> quantidadePara(Nivel nivel) {
        Integer valor = nivel == Nivel.INICIANTE ? quantidadeIniciante : Integer.valueOf(quantidadeIntermediario);
        return Optional.ofNullable(valor).map(v -> new Quantidade(forma, v));
    }

    /** As quatro condições da seção 3.3 da spec H3. */
    boolean elegivelPara(PerfilFisico perfil) {
        return !perfil.poupaAlgumaDe(restricoes)
                && (equipamento == null || perfil.dispoeDe(equipamento))
                && quantidadePara(perfil.nivel()).isPresent()
                && (!noChao || perfil.aceitaChao());
    }

    public String codigo() {
        return codigo;
    }

    public String nome() {
        return nome;
    }

    public GrupoMuscular grupo() {
        return grupo;
    }

    public int ordem() {
        return ordem;
    }

    public String instrucao() {
        return instrucao;
    }

    public FormaDeQuantidade forma() {
        return forma;
    }

    public Optional<Equipamento> equipamento() {
        return Optional.ofNullable(equipamento);
    }

    public boolean noChao() {
        return noChao;
    }

    public Set<Articulacao> restricoes() {
        return restricoes;
    }

    public boolean reserva() {
        return reserva;
    }

    @Override
    public String toString() {
        return codigo;
    }
}
