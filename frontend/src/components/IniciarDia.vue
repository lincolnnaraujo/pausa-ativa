<script setup lang="ts">
import { ref } from 'vue'

import { DURACOES_DO_BLOCO_MIN, type DuracaoDoBlocoMin, META_MAXIMA_ML } from '@/api/jornada'
import type { Perfil } from '@/api/perfil'
import { mililitros, resumoDoPerfil } from '@/formatacao'
import { lerUltimaDuracao, lerUltimaMeta, metaValida } from '@/preferencias'

defineProps<{ desabilitado: boolean; perfil: Perfil }>()
const emit = defineEmits<{
  iniciar: [metaAguaMl: number, duracaoBlocoMin: DuracaoDoBlocoMin]
  editarPerfil: []
}>()

const meta = ref<number | ''>(lerUltimaMeta())
const duracao = ref<DuracaoDoBlocoMin>(lerUltimaDuracao())
const invalida = ref(false)

function enviar() {
  const valor = Number(meta.value)
  invalida.value = meta.value === '' || !metaValida(valor)
  if (!invalida.value) {
    emit('iniciar', valor, duracao.value)
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
      A cada 30 min de trabalho, um lembrete pede um pouco de água: a meta é dividida em 16 lembretes. A
      cada hora, vem um bloco curto de exercícios.
    </p>
    <label for="meta-agua">Meta de água do dia (ml)</label>
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
    <p
      id="meta-agua-ajuda"
      :class="{ invalida }"
      data-testid="ajuda-meta"
    >
      {{ invalida ? 'Informe' : 'Escolha' }} uma meta inteira entre 1 e {{ mililitros(META_MAXIMA_ML) }}.
    </p>

    <fieldset data-testid="duracao-do-bloco">
      <legend>Blocos de exercício</legend>
      <label
        v-for="opcao in DURACOES_DO_BLOCO_MIN"
        :key="opcao"
      >
        <input
          v-model="duracao"
          type="radio"
          name="duracao-bloco"
          :value="opcao"
        >
        {{ opcao }} min
      </label>
      <p class="ajuda">
        10 min para os dias mais tranquilos. Um bloco adiado faz o seguinte ter 10 min.
      </p>
    </fieldset>

    <p
      class="perfil"
      data-testid="resumo-do-perfil"
    >
      <span>Perfil: {{ resumoDoPerfil(perfil) }}</span>
      <button
        type="button"
        class="link"
        @click="emit('editarPerfil')"
      >
        Editar perfil
      </button>
    </p>

    <button
      type="submit"
      class="botao"
      :disabled="desabilitado"
    >
      Iniciar dia
    </button>
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

label[for='meta-agua'],
legend {
  display: block;
  margin-bottom: 0.25rem;
  padding: 0;
  font-weight: 600;
}

/* A borda do campo precisa de 3:1 com o cartão para o campo ser visto (WCAG 1.4.11). */
input[type='number'] {
  width: 8rem;
  padding: 0.5rem;
  border: 1px solid var(--borda-campo);
  border-radius: 0.5rem;
  background: var(--fundo);
  color: var(--texto);
  font: inherit;
}

#meta-agua-ajuda,
.ajuda {
  margin: 0.5rem 0 0;
  font-size: 0.875rem;
  color: var(--texto-suave);
}

#meta-agua-ajuda.invalida {
  color: var(--erro);
}

fieldset {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 1.25rem;
  margin: 1rem 0;
  padding: 0;
  border: 0;
}

fieldset label {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  cursor: pointer;
}

fieldset .ajuda {
  flex-basis: 100%;
  margin-top: 0.25rem;
}

.perfil {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.25rem 0.75rem;
  margin: 0 0 1rem;
  font-size: 0.875rem;
  color: var(--texto-suave);
}
</style>
