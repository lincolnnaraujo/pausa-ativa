<script setup lang="ts">
import type { TipoDePeriodo } from '@/api/historico'

defineProps<{
  periodo: TipoDePeriodo
  /** O período por extenso: "28 set – 4 out 2026". */
  rotulo: string
  podeAvancar: boolean
  noPeriodoDeHoje: boolean
}>()
const emit = defineEmits<{
  escolher: [periodo: TipoDePeriodo]
  anterior: []
  proximo: []
  hoje: []
}>()

const OPCOES: { periodo: TipoDePeriodo; rotulo: string }[] = [
  { periodo: 'DIA', rotulo: 'Dia' },
  { periodo: 'SEMANA', rotulo: 'Semana' },
  { periodo: 'MES', rotulo: 'Mês' },
]
</script>

<template>
  <div
    class="filtros"
    data-testid="filtros"
  >
    <div
      class="tipos"
      role="group"
      aria-label="Período"
    >
      <button
        v-for="opcao in OPCOES"
        :key="opcao.periodo"
        type="button"
        class="botao"
        :class="{ 'botao-secundario': opcao.periodo !== periodo }"
        :aria-pressed="opcao.periodo === periodo"
        @click="emit('escolher', opcao.periodo)"
      >
        {{ opcao.rotulo }}
      </button>
    </div>

    <div class="navegacao">
      <button
        type="button"
        class="botao botao-secundario seta"
        aria-label="Período anterior"
        @click="emit('anterior')"
      >
        ‹
      </button>
      <h2
        class="rotulo"
        aria-live="polite"
        data-testid="periodo"
      >
        {{ rotulo }}
      </h2>
      <button
        type="button"
        class="botao botao-secundario seta"
        aria-label="Próximo período"
        :disabled="!podeAvancar"
        @click="emit('proximo')"
      >
        ›
      </button>
    </div>

    <button
      v-if="!noPeriodoDeHoje"
      type="button"
      class="link"
      @click="emit('hoje')"
    >
      Voltar para hoje
    </button>
  </div>
</template>

<style scoped>
.filtros {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem 1rem;
}

.tipos,
.navegacao {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.tipos .botao {
  padding: 0.375rem 1rem;
}

.navegacao {
  flex: 1 1 14rem;
}

/* No celular, o período por extenso ocupa a linha toda, e o Voltar para hoje desce. */
@media (max-width: 30rem) {
  .navegacao {
    flex-basis: 100%;
  }
}

.seta {
  padding: 0.25rem 0.75rem;
  font-size: 1.25rem;
  line-height: 1;
}

.rotulo {
  flex: 1;
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  text-align: center;
}
</style>
