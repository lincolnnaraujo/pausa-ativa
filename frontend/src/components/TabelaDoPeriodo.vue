<script setup lang="ts">
import { computed } from 'vue'

import type { Categoria, ContagemDoDia, DiaDoHistorico } from '@/api/historico'
import { rotuloDaCategoria, taxa } from '@/formatacao'
import { diaCurto } from '@/periodo'

const props = defineProps<{ categoria: Categoria; dias: DiaDoHistorico[] }>()
const emit = defineEmits<{ abrirDia: [data: string] }>()

interface Linha {
  dia: DiaDoHistorico
  contagem: ContagemDoDia | undefined
}

/** Um dia por linha, até hoje: os dias futuros do período não têm o que mostrar. */
const linhas = computed<Linha[]>(() =>
  props.dias
    .filter((dia) => !dia.futuro)
    .map((dia) => ({
      dia,
      contagem: dia.categorias.find((contagem) => contagem.categoria === props.categoria),
    })),
)

function emAndamento(dia: DiaDoHistorico): boolean {
  return dia.jornada === 'EM_ANDAMENTO' || dia.jornada === 'PAUSADA'
}
</script>

<template>
  <div class="rolagem">
    <table :data-testid="`tabela-${categoria}`">
      <caption>{{ rotuloDaCategoria(categoria) }} por dia</caption>
      <thead>
        <tr>
          <th scope="col">
            Dia
          </th>
          <th scope="col">
            Taxa
          </th>
          <th scope="col">
            Concluído
          </th>
          <th scope="col">
            Falha
          </th>
          <th scope="col">
            Não entregue
          </th>
          <th scope="col">
            Não concluído
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="{ dia, contagem } in linhas"
          :key="dia.data"
          :data-data="dia.data"
        >
          <th scope="row">
            <button
              v-if="dia.jornada !== null"
              type="button"
              class="link"
              @click="emit('abrirDia', dia.data)"
            >
              {{ diaCurto(dia.data) }}
            </button>
            <template v-else>
              {{ diaCurto(dia.data) }}
            </template>
            <span
              v-if="emAndamento(dia)"
              class="em-andamento"
            > · em andamento</span>
          </th>
          <td
            v-if="contagem === undefined"
            colspan="5"
            class="sem-jornada"
          >
            sem jornada
          </td>
          <template v-else>
            <td class="taxa">
              {{ contagem.taxa === null ? '—' : taxa(contagem.taxa) }}{{ contagem.metaAtingida ? ' ✓' : '' }}
            </td>
            <td>{{ contagem.concluidos }}</td>
            <td>{{ contagem.falhas }}</td>
            <td>{{ contagem.naoEntregues }}</td>
            <td>{{ contagem.naoConcluidos }}</td>
          </template>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.rolagem {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
  font-variant-numeric: tabular-nums;
}

/* O gráfico acima já tem o título; a legenda da tabela fica para o leitor de tela. */
caption {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

th,
td {
  padding: 0.25rem 0.375rem;
  border-bottom: 1px solid var(--borda);
  text-align: right;
}

/*
 * A taxa vem logo depois do dia: no celular, dia, taxa, concluído e falha cabem sem rolar; as situações
 * fora da conta ficam por último.
 */
thead th {
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1.2;
  color: var(--texto-suave);
  vertical-align: bottom;
}

.taxa {
  font-weight: 600;
  white-space: nowrap;
}

thead th:first-child,
tbody th {
  font-weight: 400;
  text-align: left;
  white-space: nowrap;
}

tbody tr:last-child > * {
  border-bottom: 0;
}

.sem-jornada,
.em-andamento {
  color: var(--texto-suave);
}

.sem-jornada {
  text-align: left;
}
</style>
