<script setup lang="ts">
import { nextTick, ref } from 'vue'

import type { Jornada, Marco, StatusCorrigido } from '@/api/jornada'
import { horario, rotuloDaCategoria, rotuloDoStatus } from '@/formatacao'

const props = withDefaults(
  defineProps<{
    jornada: Jornada
    /** A lista da aba Hoje corrige; a do Histórico é só para leitura, mesmo no dia de hoje. */
    corrigivel?: boolean
    desabilitado?: boolean
  }>(),
  { corrigivel: false, desabilitado: false },
)
const emit = defineEmits<{ corrigir: [marcoId: string, correta: StatusCorrigido] }>()

const tabela = ref<HTMLTableElement | null>(null)

/** O lembrete com a confirmação da correção à vista, na própria linha (spec H4, seção 8). */
const confirmando = ref<string | null>(null)

/** Concluído vira falha, e falha vira concluído (D4). */
function correcaoDe(marco: Marco): StatusCorrigido {
  return marco.status === 'CONCLUIDO' ? 'FALHA' : 'CONCLUIDO'
}

function mostraCorrigir(marco: Marco): boolean {
  return props.corrigivel && marco.podeCorrigir
}

/**
 * O botão que some leva o foco junto: ele passa para o botão que aparece no lugar. Por isso o Corrigir
 * nunca fica desabilitado (ele só abre a confirmação); quem espera o servidor ou a conexão é o Sim.
 */
async function focar(marcoId: string, acao: 'sim' | 'corrigir') {
  await nextTick()
  tabela.value?.querySelector<HTMLButtonElement>(`[data-marco="${marcoId}"][data-acao="${acao}"]`)?.focus()
}

function pedirConfirmacao(marco: Marco) {
  confirmando.value = marco.id
  void focar(marco.id, 'sim')
}

function cancelar(marco: Marco) {
  confirmando.value = null
  void focar(marco.id, 'corrigir')
}

function confirmar(marco: Marco) {
  confirmando.value = null
  emit('corrigir', marco.id, correcaoDe(marco))
  void focar(marco.id, 'corrigir')
}

/** Blocos abertos na lista: cada um mostra os exercícios propostos no disparo (spec H4, seção 8). */
const abertos = ref(new Set<string>())

function alternar(marcoId: string) {
  if (!abertos.value.delete(marcoId)) {
    abertos.value.add(marcoId)
  }
}

/**
 * Horário em que o marco disparou ou, se ainda vai disparar, a previsão. A previsão parte do
 * instante do servidor (`calculadoEm`), não do relógio do computador, e só vale em andamento:
 * na pausa ninguém sabe quando o trabalho volta.
 */
function horarioDoMarco(marco: Marco): string {
  if (marco.disparadoEm !== null) {
    return horario(marco.disparadoEm)
  }
  const { status, calculadoEm, tempoTrabalhadoSegundos } = props.jornada
  if (marco.status === 'AGENDADO' && status === 'EM_ANDAMENTO') {
    const faltam = marco.segundosTrabalhadosPrevistos - tempoTrabalhadoSegundos
    return `~${horario(Date.parse(calculadoEm) + faltam * 1_000)}`
  }
  return '—'
}

/** Água: o volume. Exercício: o bloco, depois que ele foi montado no disparo. */
function detalheDoMarco(marco: Marco): string {
  if (marco.categoria === 'HIDRATACAO') {
    return `~${marco.volumeAproximadoMl} ml`
  }
  if (marco.bloco === null) {
    return 'Bloco de exercício'
  }
  const quantidade = marco.bloco.itens.length
  return `${marco.bloco.duracaoMin} min · ${quantidade} ${quantidade === 1 ? 'exercício' : 'exercícios'}`
}
</script>

<template>
  <section
    class="cartao"
    aria-labelledby="titulo-lembretes"
  >
    <h2 id="titulo-lembretes">
      Lembretes do dia
    </h2>
    <div class="rolagem">
      <table
        ref="tabela"
        data-testid="lista-de-marcos"
      >
        <thead>
          <tr>
            <th scope="col">
              Horário
            </th>
            <th scope="col">
              Lembrete
            </th>
            <th
              scope="col"
              class="detalhe"
            >
              Detalhe
            </th>
            <th scope="col">
              Situação
            </th>
          </tr>
        </thead>
        <tbody>
          <template
            v-for="marco in jornada.marcos"
            :key="marco.id"
          >
            <tr
              :data-status="marco.status"
              :data-categoria="marco.categoria"
            >
              <td>{{ horarioDoMarco(marco) }}</td>
              <td class="categoria">
                <button
                  v-if="marco.bloco"
                  type="button"
                  class="link"
                  :aria-expanded="abertos.has(marco.id)"
                  @click="alternar(marco.id)"
                >
                  {{ rotuloDaCategoria(marco.categoria) }} {{ marco.sequencia }}
                </button>
                <template v-else>
                  {{ rotuloDaCategoria(marco.categoria) }} {{ marco.sequencia }}
                </template>
              </td>
              <td class="detalhe">
                {{ detalheDoMarco(marco) }}
              </td>
              <td class="situacao">
                <span
                  class="status"
                  :class="marco.status.toLowerCase()"
                >{{ rotuloDoStatus(marco.status) }}</span>
                <span
                  v-if="marco.editadoEm"
                  class="editado"
                  :title="`Corrigido às ${horario(marco.editadoEm)}`"
                  data-testid="editado"
                > · editado</span>
                <span
                  v-if="mostraCorrigir(marco) && confirmando === marco.id"
                  class="confirmacao"
                  data-testid="confirmacao-correcao"
                  @keydown.esc="cancelar(marco)"
                >
                  <span>{{ correcaoDe(marco) === 'FALHA' ? 'Marcar como falha?' : 'Marcar como concluído?' }}</span>
                  <button
                    type="button"
                    class="link"
                    data-acao="sim"
                    :data-marco="marco.id"
                    :disabled="desabilitado"
                    @click="confirmar(marco)"
                  >
                    Sim
                  </button>
                  <button
                    type="button"
                    class="link"
                    data-acao="cancelar"
                    :data-marco="marco.id"
                    @click="cancelar(marco)"
                  >
                    Cancelar
                  </button>
                </span>
                <button
                  v-else-if="mostraCorrigir(marco)"
                  type="button"
                  class="link corrigir"
                  data-acao="corrigir"
                  :data-marco="marco.id"
                  @click="pedirConfirmacao(marco)"
                >
                  Corrigir
                </button>
              </td>
            </tr>
            <tr
              v-if="marco.bloco && abertos.has(marco.id)"
              class="bloco"
              data-testid="exercicios-do-marco"
            >
              <td colspan="4">
                <p
                  v-if="marco.bloco.compensaAdiamento"
                  class="compensa"
                >
                  Bloco de {{ marco.bloco.duracaoMin }} min, com o que foi adiado na hora anterior.
                </p>
                <ol>
                  <li
                    v-for="(item, indice) in marco.bloco.itens"
                    :key="indice"
                  >
                    {{ item.exercicio }}: <span class="quantidade">{{ item.quantidade }}</span>
                  </li>
                </ol>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
h2 {
  margin: 0 0 0.75rem;
  font-size: 1.125rem;
}

.rolagem {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-variant-numeric: tabular-nums;
}

th,
td {
  padding: 0.375rem 0.5rem;
  border-bottom: 1px solid var(--borda);
  text-align: left;
}

th {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--texto-suave);
}

tbody tr:last-child td {
  border-bottom: 0;
}

/* Os exercícios ficam colados à linha do bloco, que perde a borda de baixo. */
tr:has(+ .bloco) td {
  border-bottom: 0;
}

.bloco td {
  padding-top: 0;
  font-size: 0.875rem;
}

.bloco ol {
  margin: 0;
  padding-left: 1.5rem;
}

.bloco .compensa {
  margin: 0 0 0.25rem;
  color: var(--texto-suave);
}

.bloco .quantidade {
  color: var(--exercicio);
  font-weight: 600;
}

/* Marcador da categoria; o ícone e o nome dizem o mesmo em texto. */
.categoria {
  border-left: 3px solid var(--agua);
  white-space: nowrap;
}

tr[data-categoria='EXERCICIO'] .categoria {
  border-left-color: var(--exercicio);
}

/*
 * No celular, as quatro colunas não cabem e a situação ficaria fora da tela. O detalhe sai: o volume e
 * o bloco aparecem no cartão do lembrete.
 */
@media (max-width: 30rem) {
  .detalhe {
    display: none;
  }
}

.status {
  font-size: 0.875rem;
}

.editado {
  font-size: 0.875rem;
  color: var(--texto-suave);
}

/* Corrigir e a confirmação ficam na própria linha, depois da situação; no celular, descem. */
.corrigir,
.confirmacao {
  margin-left: 0.5rem;
  font-size: 0.875rem;
}

.confirmacao {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 0 0.5rem;
}

.confirmacao > span {
  font-weight: 600;
}

.link:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* Coral só na falha. Não entregue e não concluído ficam fora da taxa: não são falha. */
.agendado,
.nao_entregue,
.nao_concluido,
.adiado {
  color: var(--texto-suave);
}

.pendente {
  font-weight: 600;
}

.concluido {
  color: var(--sucesso);
}

.falha {
  color: var(--erro);
}
</style>
