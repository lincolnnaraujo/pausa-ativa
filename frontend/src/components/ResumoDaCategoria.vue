<script setup lang="ts">
import { computed } from 'vue'

import type { ResumoDaCategoria, TipoDePeriodo } from '@/api/historico'
import { rotuloDaCategoria, taxa } from '@/formatacao'

const props = defineProps<{ resumo: ResumoDaCategoria; periodo: TipoDePeriodo }>()

/**
 * As situações que aconteceram no período. Concluído e falha fazem a taxa; não entregue e não concluído
 * ficam fora da conta (spec H4, seção 3.1); em aberto só existe na jornada de hoje.
 */
const situacoes = computed(() =>
  [
    { rotulo: 'Concluídos', quantidade: props.resumo.concluidos },
    { rotulo: 'Falhas', quantidade: props.resumo.falhas },
    { rotulo: 'Não entregues', quantidade: props.resumo.naoEntregues },
    { rotulo: 'Não concluídos', quantidade: props.resumo.naoConcluidos },
    { rotulo: 'Em aberto', quantidade: props.resumo.emAberto },
  ].filter((situacao) => situacao.quantidade > 0),
)
</script>

<template>
  <section
    class="cartao resumo"
    :data-categoria="resumo.categoria"
    :data-testid="`resumo-${resumo.categoria}`"
  >
    <h3>{{ rotuloDaCategoria(resumo.categoria) }}</h3>
    <p
      class="taxa"
      data-testid="taxa"
    >
      {{ resumo.taxa === null ? 'sem dados' : taxa(resumo.taxa) }}
    </p>
    <p
      v-if="resumo.metaAtingida !== null"
      class="meta"
      data-testid="meta"
    >
      Meta de 80%: {{ resumo.metaAtingida ? 'atingida ✓' : 'não atingida' }}
    </p>
    <p
      v-if="periodo !== 'DIA' && resumo.diasComDados > 0"
      class="dias-na-meta"
      data-testid="dias-na-meta"
    >
      Dias na meta: {{ resumo.diasNaMeta }} de {{ resumo.diasComDados }}
    </p>
    <dl
      v-if="situacoes.length > 0"
      class="situacoes"
    >
      <template
        v-for="situacao in situacoes"
        :key="situacao.rotulo"
      >
        <dt>{{ situacao.rotulo }}</dt>
        <dd>{{ situacao.quantidade }}</dd>
      </template>
    </dl>
  </section>
</template>

<style scoped>
/* A cor da borda diz a categoria; o nome com 💧 ou 🏃 diz o mesmo em texto. */
.resumo {
  border-top: 3px solid var(--agua);
}

.resumo[data-categoria='EXERCICIO'] {
  border-top-color: var(--exercicio);
}

h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
}

.taxa {
  margin: 0.25rem 0 0;
  font-size: 2rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.meta,
.dias-na-meta {
  margin: 0.25rem 0 0;
}

.dias-na-meta {
  color: var(--texto-suave);
}

.situacoes {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.125rem 1rem;
  margin: 0.75rem 0 0;
  font-size: 0.875rem;
  font-variant-numeric: tabular-nums;
}

dt {
  color: var(--texto-suave);
}

dd {
  margin: 0;
  text-align: right;
}
</style>
