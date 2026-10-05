<script setup lang="ts">
import type { Jornada, Marco } from '@/api/jornada'
import { horario, rotuloDoStatus } from '@/formatacao'

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
              #
            </th>
            <th scope="col">
              Horário
            </th>
            <th scope="col">
              Água
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
          >
            <td>{{ marco.sequencia }}</td>
            <td>{{ horarioDoMarco(marco) }}</td>
            <td>~{{ marco.volumeAproximadoMl }} ml</td>
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
