<script setup lang="ts">
import { ref } from 'vue'

import { META_MAXIMA_ML } from '@/api/jornada'
import { mililitros } from '@/formatacao'
import { lerUltimaMeta, metaValida } from '@/preferencias'

defineProps<{ desabilitado: boolean }>()
const emit = defineEmits<{ iniciar: [metaAguaMl: number] }>()

const meta = ref<number | ''>(lerUltimaMeta())
const invalida = ref(false)

function enviar() {
  const valor = Number(meta.value)
  invalida.value = meta.value === '' || !metaValida(valor)
  if (!invalida.value) {
    emit('iniciar', valor)
  }
}
</script>

<template>
  <form
    class="cartao"
    data-testid="iniciar-dia"
    novalidate
    @submit.prevent="enviar"
  >
    <h2>Começar o dia</h2>
    <p class="explicacao">
      A cada 30 min de trabalho, um lembrete pede um pouco de água. A meta é dividida em 16 lembretes.
    </p>
    <label for="meta-agua">Meta de água do dia (ml)</label>
    <div class="linha">
      <input
        id="meta-agua"
        v-model.number="meta"
        type="number"
        inputmode="numeric"
        min="1"
        :max="META_MAXIMA_ML"
        step="1"
        :aria-invalid="invalida"
        aria-describedby="meta-agua-ajuda"
      >
      <button
        type="submit"
        class="botao"
        :disabled="desabilitado"
      >
        Iniciar dia
      </button>
    </div>
    <p
      id="meta-agua-ajuda"
      :class="{ invalida }"
      data-testid="ajuda-meta"
    >
      {{ invalida ? 'Informe' : 'Escolha' }} uma meta inteira entre 1 e {{ mililitros(META_MAXIMA_ML) }}.
    </p>
  </form>
</template>

<style scoped>
h2 {
  margin: 0 0 0.25rem;
  font-size: 1.25rem;
}

.explicacao {
  margin: 0 0 1rem;
  color: var(--texto-suave);
}

label {
  display: block;
  margin-bottom: 0.25rem;
  font-weight: 600;
}

.linha {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

/* A borda do campo precisa de 3:1 com o cartão para o campo ser visto (WCAG 1.4.11). */
input {
  width: 8rem;
  padding: 0.5rem;
  border: 1px solid var(--borda-campo);
  border-radius: 0.5rem;
  background: var(--fundo);
  color: var(--texto);
  font: inherit;
}

#meta-agua-ajuda {
  margin: 0.5rem 0 0;
  font-size: 0.875rem;
  color: var(--texto-suave);
}

#meta-agua-ajuda.invalida {
  color: var(--erro);
}
</style>
