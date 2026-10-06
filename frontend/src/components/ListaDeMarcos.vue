<script setup lang="ts">
import type { Jornada, Marco } from '@/api/jornada'
import { horario, rotuloDaCategoria, rotuloDoStatus } from '@/formatacao'

const props = defineProps<{ jornada: Jornada }>()

/**
 * Horário em que o marco disparou ou, se ainda vai disparar, a previsão. A previsão parte do
 * instante do servidor (`calculadoEm`), não do relógio do computador, e só vale em andamento:
 * na pausa ninguém sabe quando o trabalho volta.
 */
function horarioDoMarco(marco: Marco): string {
  if (marco.disparadoEm !== null) {
    return horario(marco.disparadoEm)
  }
  const { status, calculadoEm, tempoTrabalhadoSegundos } = props.jornada
  if (marco.status === 'AGENDADO' && status === 'EM_ANDAMENTO') {
    const faltam = marco.segundosTrabalhadosPrevistos - tempoTrabalhadoSegundos
    return `~${horario(Date.parse(calculadoEm) + faltam * 1_000)}`
  }
  return '—'
}

/** Água: o volume. Exercício: o bloco, depois que ele foi montado no disparo. */
function detalheDoMarco(marco: Marco): string {
  if (marco.categoria === 'HIDRATACAO') {
    return `~${marco.volumeAproximadoMl} ml`
  }
  if (marco.bloco === null) {
    return 'Bloco de exercício'
  }
  const quantidade = marco.bloco.itens.length
  return `${marco.bloco.duracaoMin} min · ${quantidade} ${quantidade === 1 ? 'exercício' : 'exercícios'}`
}
</script>

<template>
  <section
    class="cartao"
    aria-labelledby="titulo-lembretes"
  >
    <h2 id="titulo-lembretes">
      Lembretes do dia
    </h2>
    <div class="rolagem">
      <table data-testid="lista-de-marcos">
        <thead>
          <tr>
            <th scope="col">
              Horário
            </th>
            <th scope="col">
              Lembrete
            </th>
            <th
              scope="col"
              class="detalhe"
            >
              Detalhe
            </th>
            <th scope="col">
              Situação
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="marco in jornada.marcos"
            :key="marco.id"
            :data-status="marco.status"
            :data-categoria="marco.categoria"
          >
            <td>{{ horarioDoMarco(marco) }}</td>
            <td class="categoria">
              {{ rotuloDaCategoria(marco.categoria) }} {{ marco.sequencia }}
            </td>
            <td class="detalhe">
              {{ detalheDoMarco(marco) }}
            </td>
            <td>
              <span
                class="status"
                :class="marco.status.toLowerCase()"
              >{{ rotuloDoStatus(marco.status) }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
h2 {
  margin: 0 0 0.75rem;
  font-size: 1.125rem;
}

.rolagem {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-variant-numeric: tabular-nums;
}

th,
td {
  padding: 0.375rem 0.5rem;
  border-bottom: 1px solid var(--borda);
  text-align: left;
}

th {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--texto-suave);
}

tbody tr:last-child td {
  border-bottom: 0;
}

/* Marcador da categoria; o ícone e o nome dizem o mesmo em texto. */
.categoria {
  border-left: 3px solid var(--agua);
  white-space: nowrap;
}

tr[data-categoria='EXERCICIO'] .categoria {
  border-left-color: var(--exercicio);
}

/*
 * No celular, as quatro colunas não cabem e a situação ficaria fora da tela. O detalhe sai: o volume e
 * o bloco aparecem no cartão do lembrete.
 */
@media (max-width: 30rem) {
  .detalhe {
    display: none;
  }
}

.status {
  font-size: 0.875rem;
}

/* Coral só na falha. Não entregue e não concluído ficam fora da taxa: não são falha. */
.agendado,
.nao_entregue,
.nao_concluido,
.adiado {
  color: var(--texto-suave);
}

.pendente {
  font-weight: 600;
}

.concluido {
  color: var(--sucesso);
}

.falha {
  color: var(--erro);
}
</style>
