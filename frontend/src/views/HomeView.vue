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

/** HH:mm no fuso informado pelo servidor, que é a fonte da verdade sobre o horário. */
function horarioDoServidor(status: StatusDoSistema): string {
  return new Intl.DateTimeFormat('pt-BR', {
    hour: '2-digit',
    minute: '2-digit',
    timeZone: status.fuso,
  }).format(new Date(status.agora))
}

onMounted(verificarServidor)
</script>

<template>
  <main class="pagina">
    <header>
      <h1>Pausa Ativa</h1>
      <p class="subtitulo">
        Hidratação e exercício ao longo da jornada.
      </p>
    </header>

    <section
      class="cartao"
      aria-live="polite"
      data-testid="status-servidor"
      :data-estado="estado.tipo"
    >
      <p v-if="estado.tipo === 'carregando'">
        Verificando o servidor…
      </p>

      <template v-else-if="estado.tipo === 'no-ar'">
        <p class="titulo-status">
          <span
            class="indicador no-ar"
            aria-hidden="true"
          />
          Servidor no ar
        </p>
        <dl>
          <dt>Versão</dt>
          <dd data-testid="versao">
            {{ estado.status.versao }}
          </dd>
          <dt>Horário do servidor</dt>
          <dd data-testid="horario">
            {{ horarioDoServidor(estado.status) }}
          </dd>
        </dl>
      </template>

      <template v-else>
        <p class="titulo-status">
          <span
            class="indicador indisponivel"
            aria-hidden="true"
          />
          Servidor indisponível
        </p>
        <p>
          Não foi possível falar com o backend. Confira se a aplicação está no ar com
          <code>docker compose ps</code>.
        </p>
        <button
          type="button"
          @click="verificarServidor"
        >
          Tentar novamente
        </button>
      </template>
    </section>
  </main>
</template>

<style scoped>
.pagina {
  max-width: 32rem;
  margin: 0 auto;
  padding: 3rem 1rem;
}

h1 {
  margin: 0;
  font-size: 2rem;
}

.subtitulo {
  margin: 0.25rem 0 2rem;
  color: var(--texto-suave);
}

.cartao {
  padding: 1.25rem 1.5rem;
  border: 1px solid var(--borda);
  border-radius: 0.75rem;
  background: var(--fundo-cartao);
}

.titulo-status {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0;
  font-weight: 600;
}

.indicador {
  width: 0.625rem;
  height: 0.625rem;
  border-radius: 50%;
}

.indicador.no-ar {
  background: var(--sucesso);
}

.indicador.indisponivel {
  background: var(--erro);
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

button {
  padding: 0.5rem 1rem;
  border: 0;
  border-radius: 0.5rem;
  background: var(--destaque);
  color: #fff;
  font: inherit;
  cursor: pointer;
}
</style>
