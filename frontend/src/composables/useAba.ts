import { onScopeDispose, ref } from 'vue'

export type Aba = 'hoje' | 'historico'

/** `#historico` abre o Histórico; qualquer outro endereço, Hoje. */
export function abaDoEndereco(hash: string): Aba {
  return hash === '#historico' ? 'historico' : 'hoje'
}

/**
 * A aba ativa fica no endereço, sem router (decisão D9 da spec H4): recarregar volta para ela, e o
 * Voltar do navegador troca de aba. As abas são links para `#hoje` e `#historico`.
 */
export function useAba() {
  const aba = ref<Aba>(abaDoEndereco(window.location.hash))

  const aoMudarOEndereco = () => (aba.value = abaDoEndereco(window.location.hash))
  window.addEventListener('hashchange', aoMudarOEndereco)
  onScopeDispose(() => window.removeEventListener('hashchange', aoMudarOEndereco))

  function ir(nova: Aba) {
    window.location.hash = nova
    aba.value = nova
  }

  return { aba, ir }
}
