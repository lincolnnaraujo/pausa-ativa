<script setup lang="ts">
import { ref, watch } from 'vue'

import { buscarStatus } from '@/api/sistema'
import type { EstadoDaConexao } from '@/composables/useEventos'

const props = defineProps<{ conexao: EstadoDaConexao }>()

/** Versão do backend, buscada a cada conexão: depois de uma queda, pode ter subido outra. */
const versao = ref<string | null>(null)

watch(
  () => props.conexao,
  async (conexao) => {
    if (conexao !== 'conectada') {
      return
    }
    try {
      versao.value = (await buscarStatus()).versao
    } catch {
      versao.value = null
    }
  },
  { immediate: true },
)
</script>

<template>
  <footer
    class="rodape"
    aria-live="polite"
    data-testid="status-servidor"
    :data-estado="conexao"
  >
    <span
      class="indicador"
      :class="conexao"
      aria-hidden="true"
    />
    <template v-if="conexao === 'conectando'">
      Conectando ao servidor…
    </template>
    <template v-else-if="conexao === 'conectada'">
      Conectado ao servidor<template v-if="versao">
        · versão <span data-testid="versao">{{ versao }}</span>
      </template>
    </template>
    <template v-else>
      Sem conexão com o servidor. Reconectando…
    </template>
  </footer>
</template>

<style scoped>
.rodape {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  font-size: 0.8125rem;
  color: var(--texto-suave);
}

.indicador {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
  background: var(--texto-suave);
}

.indicador.conectada {
  background: var(--sucesso);
}

.indicador.reconectando {
  background: var(--erro);
}
</style>
