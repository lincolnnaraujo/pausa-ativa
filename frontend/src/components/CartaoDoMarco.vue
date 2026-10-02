<script setup lang="ts">
import type { Marco } from '@/api/jornada'
import { horario } from '@/formatacao'

defineProps<{ marco: Marco; total: number; ocupado: boolean }>()
const emit = defineEmits<{ concluir: [marcoId: string]; falhar: [marcoId: string] }>()
</script>

<template>
  <section
    class="cartao lembrete"
    data-testid="marco-pendente"
    :aria-labelledby="`lembrete-${marco.id}`"
  >
    <h2 :id="`lembrete-${marco.id}`">
      Hora da água 💧
    </h2>
    <p class="mensagem">
      {{ marco.mensagem }}
    </p>
    <p
      v-if="marco.disparadoEm"
      class="detalhe"
    >
      Lembrete {{ marco.sequencia }} de {{ total }}, às {{ horario(marco.disparadoEm) }}
    </p>
    <div class="botoes">
      <button
        type="button"
        class="botao"
        :disabled="ocupado"
        @click="emit('concluir', marco.id)"
      >
        Concluir
      </button>
      <button
        type="button"
        class="botao botao-secundario"
        :disabled="ocupado"
        @click="emit('falhar', marco.id)"
      >
        Falhar
      </button>
    </div>
  </section>
</template>

<style scoped>
.lembrete {
  border: 2px solid var(--destaque);
}

h2 {
  margin: 0;
  font-size: 1.25rem;
}

.mensagem {
  margin: 0.25rem 0;
  font-size: 1.125rem;
}

.detalhe {
  margin: 0 0 1rem;
  font-size: 0.875rem;
  color: var(--texto-suave);
}
</style>
