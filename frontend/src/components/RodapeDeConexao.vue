<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { buscarStatus, type StatusDoSistema } from '@/api/sistema'

type Estado =
  | { tipo: 'carregando' }
  | { tipo: 'no-ar'; status: StatusDoSistema }
  | { tipo: 'indisponivel' }

const estado = ref<Estado>({ tipo: 'carregando' })

async function verificarServidor() {
  estado.value = { tipo: 'carregando' }
  try {
    estado.value = { tipo: 'no-ar', status: await buscarStatus() }
  } catch {
    estado.value = { tipo: 'indisponivel' }
  }
}

onMounted(verificarServidor)
</script>

<template>
  <footer
    class="rodape"
    aria-live="polite"
    data-testid="status-servidor"
    :data-estado="estado.tipo"
  >
    <template v-if="estado.tipo === 'carregando'">
      Verificando o servidor…
    </template>

    <template v-else-if="estado.tipo === 'no-ar'">
      <span
        class="indicador no-ar"
        aria-hidden="true"
      />
      Servidor no ar · versão <span data-testid="versao">{{ estado.status.versao }}</span>
    </template>

    <template v-else>
      <span
        class="indicador indisponivel"
        aria-hidden="true"
      />
      Servidor indisponível.
      <button
        type="button"
        class="link"
        @click="verificarServidor"
      >
        Tentar novamente
      </button>
    </template>
  </footer>
</template>

<style scoped>
.rodape {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  margin-top: 2rem;
  font-size: 0.8125rem;
  color: var(--texto-suave);
}

.indicador {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
}

.indicador.no-ar {
  background: var(--sucesso);
}

.indicador.indisponivel {
  background: var(--erro);
}

.link {
  padding: 0;
  border: 0;
  background: none;
  color: var(--destaque);
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}
</style>
