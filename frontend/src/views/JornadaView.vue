<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import { confirmarRecebimento, type DuracaoDoBlocoMin, type Marco } from '@/api/jornada'
import type { Perfil } from '@/api/perfil'
import AvisosDoNavegador from '@/components/AvisosDoNavegador.vue'
import CartaoDoMarco from '@/components/CartaoDoMarco.vue'
import FormularioDoPerfil from '@/components/FormularioDoPerfil.vue'
import IniciarDia from '@/components/IniciarDia.vue'
import ListaDeMarcos from '@/components/ListaDeMarcos.vue'
import PainelDaJornada from '@/components/PainelDaJornada.vue'
import ResumoDoDia from '@/components/ResumoDoDia.vue'
import RodapeDeConexao from '@/components/RodapeDeConexao.vue'
import { useEventos } from '@/composables/useEventos'
import { useJornada } from '@/composables/useJornada'
import { useNotificacoes } from '@/composables/useNotificacoes'
import { usePerfil } from '@/composables/usePerfil'
import { useSom } from '@/composables/useSom'
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
  aplicar,
  carregar,
  recarregar,
  iniciar,
  pausar,
  retomar,
  finalizar,
  concluir,
  falhar,
} = useJornada()
const {
  perfil,
  carga: cargaDoPerfil,
  salvando: salvandoPerfil,
  erro: erroDoPerfil,
  carregar: carregarPerfil,
  recarregar: recarregarPerfil,
  salvar: salvarPerfil,
} = usePerfil()
const { permissao, pedirPermissao, notificar } = useNotificacoes()
const { ligado: somLigado, bloqueado: somBloqueado, tocar } = useSom()

/** A pessoa pediu para editar o perfil; ele pode mudar a qualquer momento (spec H3, seção 3.1). */
const editandoPerfil = ref(false)

/** Marcos com recebimento confirmado, ou a caminho, por esta aba. */
const confirmados = new Set<string>()
/** Marcos já anunciados por notificação e som nesta aba. */
const avisados = new Set<string>()

/**
 * Lembrete pendente à vista chegou à tela (spec H2, seção 3.3). Sem a confirmação, o prazo vencido
 * vira NAO_ENTREGUE. Se ela falhar, a próxima sincronização tenta de novo.
 */
function confirmar(marco: Marco) {
  if (marco.recebidoEm !== null || confirmados.has(marco.id)) {
    return
  }
  confirmados.add(marco.id)
  confirmarRecebimento(marco.id).catch(() => confirmados.delete(marco.id))
}

function avisar(marco: Marco) {
  if (avisados.has(marco.id)) {
    return
  }
  avisados.add(marco.id)
  notificar(marco)
  tocar()
}

const { estado: conexao } = useEventos({
  aoDispararMarco(marco) {
    confirmar(marco)
    avisar(marco)
  },
  aoAtualizarJornada: aplicar,
  /**
   * Eventos podem ter se perdido antes da conexão abrir ou durante a queda: busca a situação real.
   * Depois de uma queda, anuncia os pendentes que nenhuma aba recebeu.
   */
  async aoConectar({ reconexao }) {
    await recarregar()
    if (reconexao) {
      pendentes.value.filter((marco) => marco.recebidoEm === null).forEach(avisar)
    }
  },
})

watch(pendentes, (marcos) => marcos.forEach(confirmar))

const desconectado = computed(() => conexao.value === 'reconectando')
const desabilitado = computed(() => ocupado.value || desconectado.value)

const aberta = computed(
  () => jornada.value?.status === 'EM_ANDAMENTO' || jornada.value?.status === 'PAUSADA',
)

/** "Lembrete 2 de 16" na água, "1 de 8" no exercício: cada categoria tem a sua contagem. */
function totalDaCategoria(categoria: Marco['categoria']): number {
  return jornada.value?.marcos.filter((marco) => marco.categoria === categoria).length ?? 0
}

/** Sem perfil, o formulário vem antes do Iniciar dia, e não dá para pular (Cenário 6 da H3). */
const perfilObrigatorio = computed(
  () => jornada.value === null && cargaDoPerfil.value === 'pronta' && perfil.value === null,
)
const mostrarFormularioDoPerfil = computed(() => editandoPerfil.value || perfilObrigatorio.value)

const carregando = computed(
  () => jornada.value === null && (carga.value === 'carregando' || cargaDoPerfil.value === 'carregando'),
)
const indisponivel = computed(
  () => jornada.value === null && (carga.value === 'indisponivel' || cargaDoPerfil.value === 'indisponivel'),
)

function carregarTudo() {
  return Promise.all([carregar(), carregarPerfil()])
}

/**
 * O pedido de permissão vai dentro do clique em Iniciar dia: o Chrome exige um gesto da pessoa. Se o
 * backend recusar (por exemplo, 409 por falta de perfil), a tela busca o perfil de novo e, sem ele,
 * mostra o formulário.
 */
async function iniciarDia(metaAguaMl: number, duracaoBlocoMin: DuracaoDoBlocoMin) {
  void pedirPermissao()
  if (!(await iniciar(metaAguaMl, duracaoBlocoMin))) {
    await recarregarPerfil()
  }
}

async function salvarPerfilFisico(novo: Perfil) {
  if (await salvarPerfil(novo)) {
    editandoPerfil.value = false
    erro.value = null
  }
}

onMounted(carregarTudo)
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
      v-if="desconectado"
      class="faixa-reconectando"
      role="status"
      data-testid="reconectando"
    >
      Sem conexão com o servidor. Reconectando… Os botões voltam quando a conexão voltar.
    </p>

    <AvisosDoNavegador
      :permissao="permissao"
      :som-bloqueado="somBloqueado && aberta"
      @pedir-permissao="pedirPermissao"
    />

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
      v-if="carregando"
      class="cartao"
      data-testid="carregando"
    >
      Carregando a jornada…
    </p>

    <section
      v-else-if="indisponivel"
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
        @click="carregarTudo"
      >
        Tentar novamente
      </button>
    </section>

    <FormularioDoPerfil
      v-else-if="mostrarFormularioDoPerfil"
      :perfil="perfil"
      :obrigatorio="perfilObrigatorio"
      :desabilitado="desabilitado || salvandoPerfil"
      :erro="erroDoPerfil"
      @salvar="salvarPerfilFisico"
      @cancelar="editandoPerfil = false"
    />

    <IniciarDia
      v-else-if="jornada === null && perfil !== null"
      :desabilitado="desabilitado"
      :perfil="perfil"
      @iniciar="iniciarDia"
      @editar-perfil="editandoPerfil = true"
    />

    <template v-else-if="jornada !== null">
      <template v-if="aberta">
        <CartaoDoMarco
          v-for="marco in pendentes"
          :key="marco.id"
          :marco="marco"
          :total="totalDaCategoria(marco.categoria)"
          :desabilitado="desabilitado"
          @concluir="concluir"
          @falhar="falhar"
        />
        <PainelDaJornada
          :jornada="jornada"
          :tempo-trabalhado-segundos="tempoTrabalhadoSegundos"
          :segundos-ate-o-proximo="segundosAteOProximo"
          :desabilitado="desabilitado"
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

    <div class="preferencias">
      <label>
        <input
          v-model="somLigado"
          type="checkbox"
          data-testid="som"
        >
        Tocar um som nos lembretes
      </label>
      <button
        v-if="jornada !== null && perfil !== null && !mostrarFormularioDoPerfil"
        type="button"
        class="link"
        @click="editandoPerfil = true"
      >
        Editar perfil físico
      </button>
      <RodapeDeConexao :conexao="conexao" />
    </div>
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
.faixa-reconectando,
.erro {
  margin: 0;
  padding: 0.75rem 1rem;
  border-radius: 0.5rem;
}

.faixa-reconectando {
  background: var(--fundo-erro);
  color: var(--erro);
  font-weight: 600;
}

.preferencias {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem 1rem;
  margin-top: 1rem;
  font-size: 0.875rem;
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
