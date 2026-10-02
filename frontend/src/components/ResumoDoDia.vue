<script setup lang="ts">
import { computed } from 'vue'

import type { Jornada, StatusMarco } from '@/api/jornada'
import { duracao, horario, mililitros, rotuloDoStatus } from '@/formatacao'

const props = defineProps<{ jornada: Jornada }>()

const ORDEM: StatusMarco[] = ['CONCLUIDO', 'FALHA', 'NAO_ENTREGUE', 'NAO_CONCLUIDO']

/** Quantos lembretes terminaram em cada situação; só as que aparecem no dia. */
const contagem = computed(() =>
  ORDEM.map((status) => ({
    status,
    quantidade: props.jornada.marcos.filter((marco) => marco.status === status).length,
  })).filter(({ quantidade }) => quantidade > 0),
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
      <template
        v-for="{ status, quantidade } in contagem"
        :key="status"
      >
        <dt>{{ rotuloDoStatus(status) }}</dt>
        <dd :data-testid="`contagem-${status}`">
          {{ quantidade }}
        </dd>
      </template>
    </dl>
    <p class="nota">
      Um novo dia pode ser iniciado amanhã. O resumo completo, com gráficos, chega numa próxima versão.
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
  margin: 0;
}

dt {
  color: var(--texto-suave);
}

dd {
  margin: 0;
  font-variant-numeric: tabular-nums;
}

.nota {
  margin: 1rem 0 0;
  font-size: 0.875rem;
  color: var(--texto-suave);
}
</style>
