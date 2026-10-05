<script setup lang="ts">
import { ref } from 'vue'

import type { Jornada } from '@/api/jornada'
import { duracao, horario, mililitros, tempoAte } from '@/formatacao'

defineProps<{
  jornada: Jornada
  tempoTrabalhadoSegundos: number
  segundosAteOProximo: number | null
  desabilitado: boolean
}>()

const emit = defineEmits<{ pausar: []; retomar: []; finalizar: [] }>()

/** Finalizar descarta os lembretes restantes: pede confirmação na própria tela (spec H2, seção 8). */
const confirmandoFinalizacao = ref(false)

function finalizar() {
  confirmandoFinalizacao.value = false
  emit('finalizar')
}
</script>

<template>
  <section
    class="cartao painel"
    data-testid="painel"
    :data-status="jornada.status"
  >
    <p
      class="situacao"
      data-testid="situacao"
    >
      <template v-if="jornada.status === 'PAUSADA' && jornada.pausadaDesde">
        Pausado desde {{ horario(jornada.pausadaDesde) }}
      </template>
      <template v-else-if="jornada.status === 'PAUSADA'">
        Pausado
      </template>
      <template v-else>
        Em andamento desde {{ horario(jornada.iniciadaEm) }}
      </template>
    </p>

    <dl class="numeros">
      <div>
        <dt>Tempo trabalhado</dt>
        <dd
          class="grande"
          data-testid="tempo-trabalhado"
        >
          {{ duracao(tempoTrabalhadoSegundos) }}
        </dd>
      </div>
      <div>
        <dt>Água do dia</dt>
        <dd data-testid="agua">
          <span class="grande agua">{{ mililitros(jornada.aguaIngeridaMl) }}</span>
          de {{ mililitros(jornada.metaAguaMl) }}
        </dd>
      </div>
      <div>
        <dt>Próximo lembrete</dt>
        <dd data-testid="proximo">
          <template v-if="segundosAteOProximo === null">
            Sem mais lembretes hoje
          </template>
          <template v-else-if="jornada.status === 'PAUSADA'">
            Parado durante a pausa
          </template>
          <template v-else>
            {{ tempoAte(segundosAteOProximo) }}
          </template>
        </dd>
      </div>
    </dl>

    <progress
      :value="jornada.aguaIngeridaMl"
      :max="jornada.metaAguaMl"
      aria-label="Água do dia em relação à meta"
    />

    <div
      v-if="confirmandoFinalizacao"
      class="confirmacao"
      role="alertdialog"
      aria-labelledby="pergunta-finalizar"
      data-testid="confirmacao-finalizar"
    >
      <p id="pergunta-finalizar">
        Finalizar? Os lembretes restantes não serão contados.
      </p>
      <div class="botoes">
        <button
          type="button"
          class="botao botao-perigo"
          :disabled="desabilitado"
          @click="finalizar"
        >
          Sim, finalizar
        </button>
        <button
          type="button"
          class="botao botao-secundario"
          @click="confirmandoFinalizacao = false"
        >
          Cancelar
        </button>
      </div>
    </div>

    <div
      v-else
      class="botoes"
    >
      <button
        v-if="jornada.status === 'EM_ANDAMENTO'"
        type="button"
        class="botao"
        :disabled="desabilitado"
        @click="emit('pausar')"
      >
        Pausar
      </button>
      <button
        v-else
        type="button"
        class="botao"
        :disabled="desabilitado"
        @click="emit('retomar')"
      >
        Retomar
      </button>
      <button
        type="button"
        class="botao botao-secundario"
        :disabled="desabilitado"
        @click="confirmandoFinalizacao = true"
      >
        Finalizar dia
      </button>
    </div>
  </section>
</template>

<style scoped>
.situacao {
  margin: 0 0 1rem;
  color: var(--texto-suave);
}

.painel[data-status='PAUSADA'] .situacao {
  color: var(--aviso);
  font-weight: 600;
}

.numeros {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(9rem, 1fr));
  gap: 1rem;
  margin: 0 0 1rem;
}

dt {
  font-size: 0.875rem;
  color: var(--texto-suave);
}

dd {
  margin: 0;
  font-variant-numeric: tabular-nums;
}

.grande {
  font-size: 1.5rem;
  font-weight: 600;
}

.agua {
  color: var(--agua);
}

progress {
  width: 100%;
  margin-bottom: 1rem;
  accent-color: var(--agua);
}

.confirmacao p {
  margin: 0 0 0.5rem;
  font-weight: 600;
}
</style>
