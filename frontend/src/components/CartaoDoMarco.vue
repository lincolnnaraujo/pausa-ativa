<script setup lang="ts">
import type { Marco } from '@/api/jornada'
import { duracaoCurta, horario, tituloDoMarco } from '@/formatacao'

defineProps<{ marco: Marco; total: number; desabilitado: boolean }>()
const emit = defineEmits<{
  concluir: [marcoId: string]
  adiar: [marcoId: string]
  falhar: [marcoId: string]
}>()
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

    <template v-if="marco.bloco">
      <p
        v-if="marco.bloco.compensaAdiamento"
        class="compensa"
        data-testid="compensa-adiamento"
      >
        Este bloco inclui o que foi adiado na hora anterior: concluir ou marcar falha vale para os dois.
      </p>
      <ol
        class="exercicios"
        data-testid="exercicios-do-bloco"
      >
        <li
          v-for="(item, indice) in marco.bloco.itens"
          :key="indice"
        >
          <span class="nome">{{ item.exercicio }}</span>
          <span class="quantidade">{{ item.quantidade }}</span>
          <span class="instrucao">{{ item.instrucao }}</span>
        </li>
      </ol>
      <p class="detalhe">
        Cerca de {{ duracaoCurta(marco.bloco.segundosEstimados) }}, contando as trocas de exercício.
      </p>
    </template>

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
        v-if="marco.podeAdiar"
        type="button"
        class="botao botao-secundario"
        :disabled="desabilitado"
        @click="emit('adiar', marco.id)"
      >
        Adiar
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

.compensa {
  margin: 0 0 1rem;
  padding: 0.5rem 0.75rem;
  border-left: 3px solid var(--exercicio);
  background: var(--fundo);
  font-size: 0.875rem;
}

.exercicios {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 0 0 0.75rem;
  padding-left: 1.5rem;
}

.exercicios li {
  display: flex;
  flex-wrap: wrap;
  gap: 0 0.5rem;
}

.nome {
  font-weight: 600;
}

.quantidade {
  color: var(--exercicio);
  font-weight: 600;
}

.instrucao {
  flex-basis: 100%;
  font-size: 0.875rem;
  color: var(--texto-suave);
}
</style>
