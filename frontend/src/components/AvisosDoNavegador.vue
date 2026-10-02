<script setup lang="ts">
import type { PermissaoDeNotificacao } from '@/composables/useNotificacoes'

defineProps<{ permissao: PermissaoDeNotificacao; somBloqueado: boolean }>()
const emit = defineEmits<{ pedirPermissao: [] }>()
</script>

<template>
  <div
    v-if="permissao !== 'granted' || somBloqueado"
    class="avisos"
  >
    <section
      v-if="permissao === 'default'"
      class="aviso"
      data-testid="aviso-permissao"
      data-permissao="default"
    >
      <p>
        Ative as notificações para ver os lembretes mesmo com a aba em segundo plano.
      </p>
      <button
        type="button"
        class="botao"
        @click="emit('pedirPermissao')"
      >
        Permitir notificações
      </button>
    </section>

    <section
      v-else-if="permissao === 'denied'"
      class="aviso"
      data-testid="aviso-permissao"
      data-permissao="denied"
    >
      <p><strong>As notificações estão bloqueadas para este site.</strong></p>
      <p>
        Para liberar no Chrome, clique no ícone à esquerda do endereço da página, ative
        <strong>Notificações</strong> e, se o aviso continuar, recarregue a página. Enquanto isso,
        os lembretes só aparecem aqui, com a aba aberta.
      </p>
    </section>

    <section
      v-else-if="permissao === 'indisponivel'"
      class="aviso"
      data-testid="aviso-permissao"
      data-permissao="indisponivel"
    >
      <p>
        Este navegador não mostra notificações. Mantenha a aba do Pausa Ativa à vista para ver os
        lembretes.
      </p>
    </section>

    <p
      v-if="somBloqueado"
      class="aviso"
      data-testid="aviso-som"
    >
      O Chrome só libera o som depois de um clique. Clique em qualquer lugar da página para ouvir os
      lembretes.
    </p>
  </div>
</template>

<style scoped>
.avisos {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.aviso {
  margin: 0;
  padding: 0.75rem 1rem;
  border: 1px solid var(--aviso);
  border-radius: 0.5rem;
  background: var(--fundo-aviso);
}

.aviso p {
  margin: 0 0 0.5rem;
}

.aviso p:last-child {
  margin-bottom: 0;
}
</style>
