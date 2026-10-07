<script setup lang="ts">
import { computed } from 'vue'

import type { Jornada, Marco, StatusMarco } from '@/api/jornada'
import { duracao, horario, mililitros, rotuloDaCategoria, rotuloDoStatus } from '@/formatacao'

type Categoria = Marco['categoria']

const props = defineProps<{ jornada: Jornada }>()

const ORDEM: StatusMarco[] = ['CONCLUIDO', 'FALHA', 'NAO_ENTREGUE', 'NAO_CONCLUIDO', 'ADIADO']

/** Jornadas da v0.2.0 só têm água: a coluna do exercício só aparece se houver exercício. */
const categorias = computed<Categoria[]>(() =>
  (['HIDRATACAO', 'EXERCICIO'] as const).filter((categoria) =>
    props.jornada.marcos.some((marco) => marco.categoria === categoria),
  ),
)

function quantos(categoria: Categoria, status: StatusMarco): number {
  return props.jornada.marcos.filter((marco) => marco.categoria === categoria && marco.status === status)
    .length
}

/** Só as situações que aparecem no dia, em qualquer categoria. */
const situacoes = computed(() =>
  ORDEM.filter((status) => categorias.value.some((categoria) => quantos(categoria, status) > 0)),
)
</script>

<template>
  <section
    class="cartao"
    data-testid="resumo-do-dia"
  >
    <h2 v-if="jornada.finalizadaEm">
      {{ jornada.status === 'ENCERRADA_AUTOMATICAMENTE' ? 'Dia encerrado automaticamente' : 'Dia finalizado' }}
      às {{ horario(jornada.finalizadaEm) }}
    </h2>
    <dl>
      <dt>Tempo trabalhado</dt>
      <dd>{{ duracao(jornada.tempoTrabalhadoSegundos) }}</dd>
      <dt>Água do dia</dt>
      <dd data-testid="agua-total">
        {{ mililitros(jornada.aguaIngeridaMl) }} de {{ mililitros(jornada.metaAguaMl) }}
      </dd>
    </dl>

    <table data-testid="contagem-por-categoria">
      <caption>Lembretes por situação</caption>
      <thead>
        <tr>
          <th scope="col">
            Situação
          </th>
          <th
            v-for="categoria in categorias"
            :key="categoria"
            scope="col"
          >
            {{ rotuloDaCategoria(categoria) }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="status in situacoes"
          :key="status"
        >
          <th scope="row">
            {{ rotuloDoStatus(status) }}
          </th>
          <td
            v-for="categoria in categorias"
            :key="categoria"
            :data-testid="`contagem-${categoria}-${status}`"
          >
            {{ quantos(categoria, status) }}
          </td>
        </tr>
      </tbody>
    </table>

    <p class="nota">
      Um novo dia pode ser iniciado amanhã. Este dia e os anteriores ficam na aba Histórico.
    </p>
  </section>
</template>

<style scoped>
h2 {
  margin: 0 0 0.75rem;
  font-size: 1.25rem;
}

dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.25rem 1rem;
  margin: 0 0 1rem;
}

dt {
  color: var(--texto-suave);
}

dd {
  margin: 0;
  font-variant-numeric: tabular-nums;
}

table {
  border-collapse: collapse;
  font-variant-numeric: tabular-nums;
}

caption {
  margin-bottom: 0.25rem;
  font-weight: 600;
  text-align: left;
}

th,
td {
  padding: 0.25rem 1rem 0.25rem 0;
  border-bottom: 1px solid var(--borda);
  text-align: left;
}

thead th,
tbody th {
  font-weight: 400;
  color: var(--texto-suave);
}

td {
  text-align: right;
}

.nota {
  margin: 1rem 0 0;
  font-size: 0.875rem;
  color: var(--texto-suave);
}
</style>
