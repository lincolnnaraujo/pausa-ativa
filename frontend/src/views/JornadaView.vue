<script setup lang="ts">
import { computed, onMounted } from 'vue'

import CartaoDoMarco from '@/components/CartaoDoMarco.vue'
import IniciarDia from '@/components/IniciarDia.vue'
import ListaDeMarcos from '@/components/ListaDeMarcos.vue'
import PainelDaJornada from '@/components/PainelDaJornada.vue'
import ResumoDoDia from '@/components/ResumoDoDia.vue'
import RodapeDeConexao from '@/components/RodapeDeConexao.vue'
import { useJornada } from '@/composables/useJornada'
import { intervalo } from '@/formatacao'

const {
  jornada,
  carga,
  ocupado,
  erro,
  tempoTrabalhadoSegundos,
  pendentes,
  segundosAteOProximo,
  intervaloSegundos,
  modoDemonstracao,
  carregar,
  iniciar,
  pausar,
  retomar,
  finalizar,
  concluir,
  falhar,
} = useJornada()

const aberta = computed(
  () => jornada.value?.status === 'EM_ANDAMENTO' || jornada.value?.status === 'PAUSADA',
)

onMounted(carregar)
</script>

<template>
  <main class="pagina">
    <header>
      <h1>Pausa Ativa</h1>
      <p class="subtitulo">
        Hidratação e exercício ao longo da jornada.
      </p>
    </header>

    <p
      v-if="modoDemonstracao && intervaloSegundos !== null"
      class="faixa-demonstracao"
      data-testid="modo-demonstracao"
    >
      Modo demonstração: um lembrete a cada {{ intervalo(intervaloSegundos) }} de tempo trabalhado.
    </p>

    <p
      v-if="erro"
      class="erro"
      role="alert"
      data-testid="erro"
    >
      {{ erro }}
    </p>

    <p
      v-if="carga === 'carregando' && jornada === null"
      class="cartao"
      data-testid="carregando"
    >
      Carregando a jornada…
    </p>

    <section
      v-else-if="carga === 'indisponivel' && jornada === null"
      class="cartao"
      data-testid="jornada-indisponivel"
    >
      <p>
        Não foi possível carregar a jornada. Confira se a aplicação está no ar com
        <code>docker compose ps</code>.
      </p>
      <button
        type="button"
        class="botao"
        @click="carregar"
      >
        Tentar novamente
      </button>
    </section>

    <IniciarDia
      v-else-if="jornada === null"
      :ocupado="ocupado"
      @iniciar="iniciar"
    />

    <template v-else>
      <template v-if="aberta">
        <CartaoDoMarco
          v-for="marco in pendentes"
          :key="marco.id"
          :marco="marco"
          :total="jornada.marcos.length"
          :ocupado="ocupado"
          @concluir="concluir"
          @falhar="falhar"
        />
        <PainelDaJornada
          :jornada="jornada"
          :tempo-trabalhado-segundos="tempoTrabalhadoSegundos"
          :segundos-ate-o-proximo="segundosAteOProximo"
          :ocupado="ocupado"
          @pausar="pausar"
          @retomar="retomar"
          @finalizar="finalizar"
        />
      </template>
      <ResumoDoDia
        v-else
        :jornada="jornada"
      />
      <ListaDeMarcos :jornada="jornada" />
    </template>

    <RodapeDeConexao />
  </main>
</template>

<style scoped>
.pagina {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 36rem;
  margin: 0 auto;
  padding: 2rem 1rem 3rem;
}

header {
  margin-bottom: 0.5rem;
}

h1 {
  margin: 0;
  font-size: 2rem;
}

.subtitulo {
  margin: 0.25rem 0 0;
  color: var(--texto-suave);
}

.faixa-demonstracao,
.erro {
  margin: 0;
  padding: 0.75rem 1rem;
  border-radius: 0.5rem;
}

.faixa-demonstracao {
  background: var(--fundo-aviso);
  color: var(--aviso);
  font-weight: 600;
}

.erro {
  background: var(--fundo-erro);
  color: var(--erro);
}

p.cartao {
  margin: 0;
}
</style>
