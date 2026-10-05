<script setup lang="ts">
import type { Marco } from '@/api/jornada'
import { horario, tituloDoMarco } from '@/formatacao'

defineProps<{ marco: Marco; total: number; desabilitado: boolean }>()
const emit = defineEmits<{ concluir: [marcoId: string]; falhar: [marcoId: string] }>()
</script>

<template>
  <section
    class="cartao lembrete"
    data-testid="marco-pendente"
    :data-categoria="marco.categoria"
    :aria-labelledby="`lembrete-${marco.id}`"
  >
    <h2 :id="`lembrete-${marco.id}`">
      {{ tituloDoMarco(marco.categoria) }}
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
        :disabled="desabilitado"
        @click="emit('concluir', marco.id)"
      >
        Concluir
      </button>
      <button
        type="button"
        class="botao botao-perigo"
        :disabled="desabilitado"
        @click="emit('falhar', marco.id)"
      >
        Falhar
      </button>
    </div>
  </section>
</template>

<style scoped>
/* A cor da borda diz a categoria; o título com 💧 ou 🏃 diz o mesmo em texto. */
.lembrete {
  border: 2px solid var(--agua);
}

.lembrete[data-categoria='EXERCICIO'] {
  border-color: var(--exercicio);
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
