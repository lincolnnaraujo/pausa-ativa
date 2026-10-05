<script setup lang="ts">
import { reactive } from 'vue'

import { type Articulacao, type Equipamento, type Nivel, type Perfil, PERFIL_PADRAO } from '@/api/perfil'
import { ROTULOS_DE_ARTICULACAO, ROTULOS_DE_EQUIPAMENTO, ROTULOS_DE_NIVEL } from '@/formatacao'

const props = defineProps<{
  /** O perfil atual, para editar; sem ele, os padrões da spec H3, seção 3.1. */
  perfil: Perfil | null
  /** Primeiro preenchimento: sem ele o dia não começa, então não há como cancelar (Cenário 6). */
  obrigatorio: boolean
  desabilitado: boolean
  erro: string | null
}>()
const emit = defineEmits<{ salvar: [perfil: Perfil]; cancelar: [] }>()

const inicial = props.perfil ?? PERFIL_PADRAO
const respostas = reactive<Perfil>({
  articulacoesPoupadas: [...inicial.articulacoesPoupadas],
  nivel: inicial.nivel,
  equipamentos: [...inicial.equipamentos],
  aceitaChao: inicial.aceitaChao,
})

const articulacoes = Object.entries(ROTULOS_DE_ARTICULACAO) as [Articulacao, string][]
const equipamentos = Object.entries(ROTULOS_DE_EQUIPAMENTO) as [Equipamento, string][]
const niveis = Object.entries(ROTULOS_DE_NIVEL) as [Nivel, string][]

function enviar() {
  emit('salvar', {
    articulacoesPoupadas: [...respostas.articulacoesPoupadas],
    nivel: respostas.nivel,
    equipamentos: [...respostas.equipamentos],
    aceitaChao: respostas.aceitaChao,
  })
}
</script>

<template>
  <form
    class="cartao"
    data-testid="formulario-do-perfil"
    aria-labelledby="titulo-perfil"
    @submit.prevent="enviar"
  >
    <h2 id="titulo-perfil">
      {{ obrigatorio ? 'Seu perfil físico' : 'Editar perfil físico' }}
    </h2>
    <p
      v-if="obrigatorio"
      class="explicacao"
    >
      Antes do primeiro dia, conte o que você pode fazer. Os blocos de exercício de cada hora são montados
      com estas respostas, e você pode mudá-las quando quiser.
    </p>
    <p
      v-else
      class="explicacao"
    >
      As mudanças valem para os próximos blocos. Os blocos que já saíram não mudam.
    </p>

    <fieldset>
      <legend>Articulações a poupar</legend>
      <p class="ajuda">
        Exercícios que forçam essas articulações ficam de fora.
      </p>
      <label
        v-for="[valor, rotulo] in articulacoes"
        :key="valor"
      >
        <input
          v-model="respostas.articulacoesPoupadas"
          type="checkbox"
          name="articulacao"
          :value="valor"
        >
        {{ rotulo }}
      </label>
    </fieldset>

    <fieldset>
      <legend>Nível</legend>
      <p class="ajuda">
        O intermediário faz mais repetições e recebe exercícios a mais.
      </p>
      <label
        v-for="[valor, rotulo] in niveis"
        :key="valor"
      >
        <input
          v-model="respostas.nivel"
          type="radio"
          name="nivel"
          :value="valor"
        >
        {{ rotulo }}
      </label>
    </fieldset>

    <fieldset>
      <legend>Equipamentos que você tem</legend>
      <p class="ajuda">
        Cadeira e mesa já contam como disponíveis.
      </p>
      <label
        v-for="[valor, rotulo] in equipamentos"
        :key="valor"
      >
        <input
          v-model="respostas.equipamentos"
          type="checkbox"
          name="equipamento"
          :value="valor"
        >
        {{ rotulo }}
      </label>
    </fieldset>

    <fieldset>
      <legend>Exercícios no chão</legend>
      <p class="ajuda">
        Deitado ou de quatro, como prancha e ponte de glúteo.
      </p>
      <label>
        <input
          v-model="respostas.aceitaChao"
          type="radio"
          name="chao"
          :value="true"
        >
        Posso fazer
      </label>
      <label>
        <input
          v-model="respostas.aceitaChao"
          type="radio"
          name="chao"
          :value="false"
        >
        Prefiro evitar
      </label>
    </fieldset>

    <p
      v-if="erro"
      class="erro"
      role="alert"
      data-testid="erro-perfil"
    >
      {{ erro }}
    </p>

    <div class="botoes">
      <button
        type="submit"
        class="botao"
        :disabled="desabilitado"
      >
        Salvar perfil
      </button>
      <button
        v-if="!obrigatorio"
        type="button"
        class="botao botao-secundario"
        @click="emit('cancelar')"
      >
        Cancelar
      </button>
    </div>

    <p class="nota">
      Este app não substitui orientação médica. Os exercícios e as quantidades são pontos de partida.
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

fieldset {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 1.25rem;
  margin: 0 0 1rem;
  padding: 0;
  border: 0;
}

legend {
  margin-bottom: 0.125rem;
  padding: 0;
  font-weight: 600;
}

.ajuda {
  flex-basis: 100%;
  margin: 0 0 0.25rem;
  font-size: 0.875rem;
  color: var(--texto-suave);
}

label {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  cursor: pointer;
}

.erro {
  margin: 0 0 1rem;
  color: var(--erro);
}

.nota {
  margin: 1rem 0 0;
  font-size: 0.8125rem;
  color: var(--texto-suave);
}
</style>
