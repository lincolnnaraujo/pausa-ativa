<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import type { Categoria } from '@/api/historico'
import FiltrosDoHistorico from '@/components/FiltrosDoHistorico.vue'
import GraficoDoPeriodo from '@/components/GraficoDoPeriodo.vue'
import ListaDeMarcos from '@/components/ListaDeMarcos.vue'
import ResumoDaCategoria from '@/components/ResumoDaCategoria.vue'
import TabelaDoPeriodo from '@/components/TabelaDoPeriodo.vue'
import { useHistorico } from '@/composables/useHistorico'
import { duracao, horario, mililitros, rotuloDaCategoria } from '@/formatacao'
import { nestePeriodo, rotuloDoPeriodo } from '@/periodo'

const props = defineProps<{
  /** A aba Histórico está à vista. Escondida, a tela não busca nada. */
  ativa: boolean
  /** Lembretes pendentes na tela Hoje. */
  pendentes: number
  /** Muda a cada `jornada-atualizada` recebido pela tela Hoje. */
  versaoDaJornada: number
}>()
const emit = defineEmits<{ irParaHoje: [] }>()

const {
  periodo,
  data,
  historico,
  jornadaDoDia,
  carga,
  buscando,
  erro,
  noPeriodoDeHoje,
  podeAvancar,
  buscar,
  escolherPeriodo,
  anterior,
  proximo,
  irParaHoje,
  abrirDia,
  aoAtualizarJornada,
} = useHistorico()

onMounted(buscar)

/** Ao voltar para a aba, os números podem ter mudado: busca de novo, sem trocar a tela por "carregando". */
watch(
  () => props.ativa,
  (ativa) => {
    if (ativa) {
      void buscar()
    }
  },
)

watch(
  () => props.versaoDaJornada,
  () => {
    if (props.ativa) {
      aoAtualizarJornada()
    }
  },
)

const rotulo = computed(() => rotuloDoPeriodo(periodo.value, data.value))

/** Período sem nenhuma jornada: só a frase, sem taxa e sem zeros (Cenário 6). */
const semJornada = computed(() => historico.value?.dias.every((dia) => dia.jornada === null) ?? false)

/** Categoria sem nenhum lembrete no período, como o exercício nas jornadas da v0.2.0. */
function semDados(categoria: string): boolean {
  return (
    historico.value?.dias.every((dia) =>
      dia.categorias
        .filter((contagem) => contagem.categoria === categoria)
        .every(
          (contagem) =>
            contagem.concluidos +
              contagem.falhas +
              contagem.naoEntregues +
              contagem.naoConcluidos +
              contagem.emAberto ===
            0,
        ),
    ) ?? true
  )
}

/**
 * A tabela equivalente de cada gráfico, com os mesmos números, um dia por linha: a dica de valores nunca
 * é o único caminho para um número (spec H4, seção 8.1). Fica aberta ao trocar de período.
 */
const tabelasAbertas = ref(new Set<Categoria>())

function alternarTabela(categoria: Categoria) {
  if (!tabelasAbertas.value.delete(categoria)) {
    tabelasAbertas.value.add(categoria)
  }
}

const jornadaEmAndamento = computed(
  () => jornadaDoDia.value?.status === 'EM_ANDAMENTO' || jornadaDoDia.value?.status === 'PAUSADA',
)
</script>

<template>
  <main
    class="tela"
    data-testid="historico"
  >
    <FiltrosDoHistorico
      :periodo="periodo"
      :rotulo="rotulo"
      :pode-avancar="podeAvancar"
      :no-periodo-de-hoje="noPeriodoDeHoje"
      @escolher="escolherPeriodo"
      @anterior="anterior"
      @proximo="proximo"
      @hoje="irParaHoje"
    />

    <p
      v-if="pendentes > 0"
      class="faixa-pendente"
      role="status"
      data-testid="faixa-pendente"
    >
      <span>
        {{ pendentes === 1 ? 'Um lembrete espera' : `${pendentes} lembretes esperam` }} resposta na aba Hoje.
      </span>
      <button
        type="button"
        class="botao"
        @click="emit('irParaHoje')"
      >
        Ir para Hoje
      </button>
    </p>

    <p
      v-if="carga === 'carregando'"
      class="cartao"
      data-testid="historico-carregando"
    >
      Carregando o histórico…
    </p>

    <section
      v-else-if="carga === 'indisponivel'"
      class="cartao"
      data-testid="historico-indisponivel"
    >
      <p
        class="erro"
        role="alert"
      >
        {{ erro }}
      </p>
      <button
        type="button"
        class="botao"
        @click="buscar"
      >
        Tentar novamente
      </button>
    </section>

    <div
      v-else-if="historico !== null"
      class="conteudo"
      :class="{ esmaecido: buscando }"
      :aria-busy="buscando"
      data-testid="conteudo-do-historico"
    >
      <p
        v-if="semJornada"
        class="cartao vazio"
        data-testid="historico-vazio"
      >
        Nenhuma jornada {{ nestePeriodo(historico.periodo) }}.
      </p>

      <template v-else>
        <div class="resumos">
          <ResumoDaCategoria
            v-for="resumo in historico.categorias"
            :key="resumo.categoria"
            :resumo="resumo"
            :periodo="historico.periodo"
          />
        </div>

        <template v-if="historico.periodo === 'DIA'">
          <section
            v-if="jornadaDoDia !== null"
            class="cartao"
            data-testid="jornada-do-dia"
          >
            <dl>
              <dt>Início</dt>
              <dd>{{ horario(jornadaDoDia.iniciadaEm) }}</dd>
              <dt>Fim</dt>
              <dd data-testid="fim-do-dia">
                <template v-if="jornadaEmAndamento">
                  em andamento
                </template>
                <template v-else-if="jornadaDoDia.finalizadaEm !== null">
                  {{ horario(jornadaDoDia.finalizadaEm) }}{{
                    jornadaDoDia.status === 'ENCERRADA_AUTOMATICAMENTE' ? ', encerrado automaticamente' : ''
                  }}
                </template>
              </dd>
              <dt>Tempo trabalhado</dt>
              <dd>{{ duracao(jornadaDoDia.tempoTrabalhadoSegundos) }}</dd>
              <dt>Água do dia</dt>
              <dd data-testid="agua-do-dia">
                {{ mililitros(jornadaDoDia.aguaIngeridaMl) }} de {{ mililitros(jornadaDoDia.metaAguaMl) }}
              </dd>
            </dl>
          </section>
          <ListaDeMarcos
            v-if="jornadaDoDia !== null"
            :key="jornadaDoDia.id"
            :jornada="jornadaDoDia"
          />
        </template>

        <template v-else>
          <section
            v-for="resumo in historico.categorias"
            :key="resumo.categoria"
            class="cartao dias"
            :data-testid="`dias-${resumo.categoria}`"
          >
            <p
              v-if="semDados(resumo.categoria)"
              class="sem-dados"
            >
              {{ rotuloDaCategoria(resumo.categoria) }}: sem dados {{ nestePeriodo(historico.periodo) }}
            </p>
            <template v-else>
              <GraficoDoPeriodo
                :resumo="resumo"
                :dias="historico.dias"
                :periodo="historico.periodo"
                :rotulo-do-periodo="rotuloDoPeriodo(historico.periodo, historico.inicio)"
                @abrir-dia="abrirDia"
              />
              <button
                type="button"
                class="link alternar-tabela"
                :aria-expanded="tabelasAbertas.has(resumo.categoria)"
                @click="alternarTabela(resumo.categoria)"
              >
                Ver como tabela
              </button>
              <TabelaDoPeriodo
                v-if="tabelasAbertas.has(resumo.categoria)"
                :categoria="resumo.categoria"
                :dias="historico.dias"
                @abrir-dia="abrirDia"
              />
            </template>
          </section>
        </template>
      </template>
    </div>
  </main>
</template>

<style scoped>
.tela {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.faixa-pendente {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem 1rem;
  margin: 0;
  padding: 0.75rem 1rem;
  border-radius: 0.5rem;
  background: var(--fundo-aviso);
  color: var(--aviso);
  font-weight: 600;
}

.conteudo {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  transition: opacity 0.15s;
}

/* Enquanto busca o período novo, o anterior fica à vista, esmaecido: sem esqueleto nem salto. */
.esmaecido {
  opacity: 0.5;
}

.resumos {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
  gap: 1rem;
}

p.cartao {
  margin: 0;
}

.alternar-tabela {
  align-self: flex-start;
  margin-top: 0.75rem;
  font-size: 0.875rem;
}

.dias {
  display: flex;
  flex-direction: column;
}

.dias .rolagem {
  margin-top: 0.5rem;
}

/* No celular, o gráfico e a tabela de dias ficam com a largura do respiro lateral do cartão. */
@media (max-width: 30rem) {
  .dias {
    padding-inline: 0.75rem;
  }
}

.vazio,
.sem-dados {
  color: var(--texto-suave);
}

.sem-dados {
  margin: 0;
}

dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.25rem 1rem;
  margin: 0;
}

dt {
  color: var(--texto-suave);
}

dd {
  margin: 0;
  font-variant-numeric: tabular-nums;
}

.erro {
  margin: 0 0 0.75rem;
  color: var(--erro);
}
</style>
