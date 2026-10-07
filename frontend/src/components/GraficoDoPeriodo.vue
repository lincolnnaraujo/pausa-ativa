<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, useId } from 'vue'

import type { DiaDoHistorico, ResumoDaCategoria, TipoDePeriodo } from '@/api/historico'
import { rotuloDaCategoria, taxa } from '@/formatacao'
import {
  ALTURA,
  type Coluna,
  descricaoDoDia,
  descricaoDoGrafico,
  geometria,
  MARGEM,
  SITUACOES,
  tituloDoGrafico,
  topoArredondado,
} from '@/grafico'
import { diaCurto } from '@/periodo'

const props = defineProps<{
  resumo: ResumoDaCategoria
  dias: DiaDoHistorico[]
  periodo: TipoDePeriodo
  /** O período por extenso, para o título do gráfico: "28 set – 4 out 2026". */
  rotuloDoPeriodo: string
}>()
const emit = defineEmits<{ abrirDia: [data: string] }>()

/**
 * Largura do desenho, em pixels, medida na caixa do gráfico: as colunas têm no máximo 24 px, e no
 * celular ficam mais finas. Até medir (e no jsdom, que não mede), vale a do cartão na página de 36rem.
 */
const LARGURA_PADRAO = 526
const caixa = ref<HTMLElement | null>(null)
const largura = ref(LARGURA_PADRAO)
let observador: ResizeObserver | undefined

onMounted(() => {
  if (caixa.value === null || typeof ResizeObserver === 'undefined') {
    return
  }
  observador = new ResizeObserver(([entrada]) => {
    const medida = Math.round(entrada?.contentRect.width ?? 0)
    if (medida > 0) {
      largura.value = medida
    }
  })
  observador.observe(caixa.value)
})
onUnmounted(() => observador?.disconnect())

const grafico = computed(() => geometria(props.dias, props.resumo.categoria, props.periodo, largura.value))

/** Só os dias com jornada são interativos: os outros não têm números nem o que abrir. */
const interativas = computed(() => grafico.value.colunas.filter((coluna) => coluna.contagem !== undefined))

const id = useId()

/** O dia sob o mouse ou com o foco do teclado, que ganha a dica de valores. */
const emFoco = ref<string | null>(null)
const colunaDaDica = computed(() => interativas.value.find((coluna) => coluna.dia.data === emFoco.value))

/** A dica fica centrada no dia, sem passar das bordas do gráfico. */
const MEIA_DICA = 112
function posicaoDaDica(coluna: Coluna): number {
  return Math.min(Math.max(coluna.centro, MEIA_DICA), Math.max(grafico.value.largura - MEIA_DICA, MEIA_DICA))
}

function emAndamento(coluna: Coluna): boolean {
  return coluna.dia.jornada === 'EM_ANDAMENTO' || coluna.dia.jornada === 'PAUSADA'
}

function rotuloDaFaixa(coluna: Coluna): string {
  return coluna.contagem === undefined ? '' : `${descricaoDoDia(coluna.dia, coluna.contagem)} Abrir o dia.`
}
</script>

<template>
  <figure
    class="grafico"
    :data-categoria="resumo.categoria"
    :data-testid="`grafico-${resumo.categoria}`"
  >
    <figcaption>
      <h3>{{ rotuloDaCategoria(resumo.categoria) }} por dia</h3>
      <ul
        class="legenda"
        aria-label="Legenda"
      >
        <li
          v-for="situacao in SITUACOES"
          :key="situacao.chave"
        >
          <span
            class="amostra"
            :class="situacao.classe"
          />{{ situacao.rotulo }}
        </li>
        <li><span class="marca-da-meta">✓</span>Dia na meta de 80%</li>
      </ul>
    </figcaption>

    <div
      ref="caixa"
      class="caixa"
    >
      <svg
        role="img"
        :aria-labelledby="`${id}-titulo ${id}-descricao`"
        :viewBox="`0 0 ${grafico.largura} ${ALTURA}`"
        :width="grafico.largura"
        :height="ALTURA"
      >
        <title :id="`${id}-titulo`">{{ tituloDoGrafico(resumo.categoria, rotuloDoPeriodo) }}</title>
        <desc :id="`${id}-descricao`">{{ descricaoDoGrafico(resumo) }}</desc>

        <g class="eixo-y">
          <template
            v-for="marca in grafico.marcas"
            :key="marca.valor"
          >
            <line
              class="grade"
              :x1="MARGEM.esquerda"
              :x2="grafico.largura - MARGEM.direita"
              :y1="marca.y"
              :y2="marca.y"
            />
            <text
              class="rotulo-y"
              :x="MARGEM.esquerda - 6"
              :y="marca.y"
              text-anchor="end"
              dominant-baseline="middle"
            >{{ marca.valor }}</text>
          </template>
        </g>

        <g
          v-for="coluna in grafico.colunas"
          :key="coluna.dia.data"
          class="dia"
          :class="{ futuro: coluna.dia.futuro, 'em-foco': coluna.dia.data === emFoco }"
          :data-data="coluna.dia.data"
        >
          <template
            v-for="segmento in coluna.segmentos"
            :key="segmento.situacao.chave"
          >
            <path
              v-if="segmento.topo"
              class="segmento"
              :class="segmento.situacao.classe"
              :d="topoArredondado(coluna.x, segmento.y, coluna.largura, segmento.altura)"
              :data-situacao="segmento.situacao.chave"
              :data-quantidade="segmento.quantidade"
            />
            <rect
              v-else
              class="segmento"
              :class="segmento.situacao.classe"
              :x="coluna.x"
              :y="segmento.y"
              :width="coluna.largura"
              :height="segmento.altura"
              :data-situacao="segmento.situacao.chave"
              :data-quantidade="segmento.quantidade"
            />
          </template>
          <text
            v-if="coluna.contagem?.metaAtingida"
            class="na-meta"
            :x="coluna.centro"
            :y="coluna.topoY - 4"
            text-anchor="middle"
          >✓</text>
          <text
            v-for="(linha, indice) in coluna.rotulo"
            :key="indice"
            class="rotulo-x"
            :x="coluna.centro"
            :y="grafico.base + 14 + indice * 13"
            text-anchor="middle"
          >{{ linha }}</text>
        </g>
      </svg>

      <div class="faixas">
        <button
          v-for="coluna in interativas"
          :key="coluna.dia.data"
          type="button"
          class="faixa"
          :style="{ left: `${coluna.faixaX}px`, width: `${coluna.faixaLargura}px` }"
          :aria-label="rotuloDaFaixa(coluna)"
          :data-data="coluna.dia.data"
          @mouseenter="emFoco = coluna.dia.data"
          @mouseleave="emFoco = null"
          @focus="emFoco = coluna.dia.data"
          @blur="emFoco = null"
          @keydown.esc="emFoco = null"
          @click="emit('abrirDia', coluna.dia.data)"
        />
      </div>

      <div
        v-if="colunaDaDica?.contagem"
        class="dica"
        aria-hidden="true"
        data-testid="dica"
        :style="{ left: `${posicaoDaDica(colunaDaDica)}px` }"
      >
        <p class="dica-dia">
          {{ diaCurto(colunaDaDica.dia.data) }}{{ emAndamento(colunaDaDica) ? ' · em andamento' : '' }}
        </p>
        <p class="dica-taxa">
          {{ colunaDaDica.contagem.taxa === null ? 'sem dados' : taxa(colunaDaDica.contagem.taxa)
          }}{{ colunaDaDica.contagem.metaAtingida ? ' ✓ na meta' : '' }}
        </p>
        <ul>
          <li
            v-for="situacao in SITUACOES"
            :key="situacao.chave"
          >
            <span
              class="amostra"
              :class="situacao.classe"
            />{{ situacao.rotulo }}: {{ colunaDaDica.contagem[situacao.chave] }}
          </li>
          <li v-if="colunaDaDica.contagem.emAberto > 0">
            Em aberto: {{ colunaDaDica.contagem.emAberto }}
          </li>
        </ul>
      </div>
    </div>
  </figure>
</template>

<style scoped>
.grafico {
  margin: 0;
}

h3 {
  margin: 0 0 0.5rem;
  font-size: 1rem;
  font-weight: 600;
}

/* A legenda fica sempre visível, acima do gráfico. */
.legenda {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 1rem;
  margin: 0 0 0.5rem;
  padding: 0;
  list-style: none;
  font-size: 0.8125rem;
  color: var(--texto-suave);
}

.legenda li,
.dica li {
  display: flex;
  align-items: center;
  gap: 0.375rem;
}

.amostra {
  display: inline-block;
  width: 0.75rem;
  height: 0.75rem;
  border-radius: 0.1875rem;
}

.marca-da-meta {
  color: var(--texto);
}

.caixa {
  position: relative;
}

svg {
  display: block;
  width: 100%;
  height: auto;
  overflow: visible;
}

/* Cada segmento tem a cor da situação; o concluído, a da categoria. */
.concluido {
  fill: var(--agua);
  background: var(--agua);
}

.grafico[data-categoria='EXERCICIO'] .concluido {
  fill: var(--exercicio);
  background: var(--exercicio);
}

.falha {
  fill: var(--erro);
  background: var(--erro);
}

.nao-entregue {
  fill: var(--nao-entregue);
  background: var(--nao-entregue);
}

.nao-concluido {
  fill: var(--nao-concluido);
  background: var(--nao-concluido);
}

.grade {
  stroke: var(--borda);
  stroke-width: 1;
}

.rotulo-y,
.rotulo-x {
  fill: var(--texto-suave);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.futuro .rotulo-x {
  opacity: 0.5;
}

.em-foco .rotulo-x {
  fill: var(--texto);
  font-weight: 600;
}

.na-meta {
  fill: var(--texto);
  font-size: 12px;
}

/* A área de toque e de foco é a faixa inteira do dia, não só a coluna pintada. */
.faixas {
  position: absolute;
  inset: 0;
}

.faixa {
  position: absolute;
  top: 0;
  bottom: 0;
  padding: 0;
  border: 0;
  border-radius: 0.25rem;
  background: transparent;
  cursor: pointer;
}

.faixa:hover {
  background: color-mix(in srgb, var(--texto) 6%, transparent);
}

/* A dica flutua acima do gráfico, sem cobrir as colunas, e não captura o mouse. */
.dica {
  position: absolute;
  bottom: calc(100% + 0.25rem);
  z-index: 1;
  width: max-content;
  max-width: 14rem;
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--borda);
  border-radius: 0.5rem;
  background: var(--fundo);
  font-size: 0.8125rem;
  pointer-events: none;
  transform: translateX(-50%);
}

.dica p {
  margin: 0;
}

.dica-dia {
  font-weight: 600;
}

.dica-taxa {
  font-size: 1rem;
  font-weight: 600;
}

.dica ul {
  margin: 0.25rem 0 0;
  padding: 0;
  list-style: none;
  color: var(--texto-suave);
}
</style>
