<script setup lang="ts">
import { ref, watch } from 'vue'

import { useAba } from './composables/useAba'
import HistoricoView from './views/HistoricoView.vue'
import JornadaView from './views/JornadaView.vue'

const { aba, ir } = useAba()

const pendentes = ref(0)
const versaoDaJornada = ref(0)

/** O Histórico é montado na primeira visita e depois só se esconde: guarda o período escolhido. */
const historicoVisitado = ref(aba.value === 'historico')
watch(aba, (atual) => {
  if (atual === 'historico') {
    historicoVisitado.value = true
  }
})
</script>

<template>
  <div class="pagina">
    <header>
      <h1>Pausa Ativa</h1>
      <p class="subtitulo">
        Hidratação e exercício ao longo da jornada.
      </p>
    </header>

    <nav
      class="abas"
      aria-label="Telas"
    >
      <a
        href="#hoje"
        class="aba"
        :aria-current="aba === 'hoje' ? 'page' : undefined"
        data-testid="aba-hoje"
      >Hoje<template v-if="pendentes > 0"> · {{ pendentes }}</template></a>
      <a
        href="#historico"
        class="aba"
        :aria-current="aba === 'historico' ? 'page' : undefined"
        data-testid="aba-historico"
      >Histórico</a>
    </nav>

    <JornadaView
      v-show="aba === 'hoje'"
      @pendentes="pendentes = $event"
      @atualizada="versaoDaJornada++"
    />
    <HistoricoView
      v-if="historicoVisitado"
      v-show="aba === 'historico'"
      :ativa="aba === 'historico'"
      :pendentes="pendentes"
      :versao-da-jornada="versaoDaJornada"
      @ir-para-hoje="ir('hoje')"
    />
  </div>
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

h1 {
  margin: 0;
  font-size: 2rem;
}

.subtitulo {
  margin: 0.25rem 0 0;
  color: var(--texto-suave);
}

.abas {
  display: flex;
  gap: 1.5rem;
  border-bottom: 1px solid var(--borda);
}

/* A aba ativa tem o sublinhado em teal e o texto cheio; a outra, texto suave. */
.aba {
  margin-bottom: -1px;
  padding: 0.5rem 0;
  border-bottom: 3px solid transparent;
  color: var(--texto-suave);
  font-weight: 600;
  text-decoration: none;
}

.aba:hover {
  color: var(--texto);
}

.aba[aria-current='page'] {
  border-bottom-color: var(--destaque);
  color: var(--texto);
}
</style>
