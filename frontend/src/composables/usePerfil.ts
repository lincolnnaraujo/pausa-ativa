import { ref, shallowRef } from 'vue'

import { buscarPerfil, type Perfil, salvarPerfil } from '@/api/perfil'

import { type EstadoDaCarga, mensagemDeErro } from './useJornada'

/** Perfil físico da tela (spec H3, seção 3.1). Sem ele, o dia não começa (Cenário 6). */
export function usePerfil() {
  const perfil = shallowRef<Perfil | null>(null)
  const carga = ref<EstadoDaCarga>('carregando')
  const salvando = ref(false)
  const erro = ref<string | null>(null)

  async function carregar() {
    carga.value = 'carregando'
    await recarregar()
  }

  /** Busca de novo sem trocar a tela por "carregando": depois de o backend recusar o início do dia. */
  async function recarregar() {
    try {
      perfil.value = await buscarPerfil()
      carga.value = 'pronta'
    } catch {
      if (carga.value === 'carregando') {
        carga.value = 'indisponivel'
      }
    }
  }

  /** @returns se o perfil foi gravado */
  async function salvar(novo: Perfil): Promise<boolean> {
    if (salvando.value) {
      return false
    }
    salvando.value = true
    erro.value = null
    try {
      perfil.value = await salvarPerfil(novo)
      return true
    } catch (falha) {
      erro.value = mensagemDeErro(falha)
      return false
    } finally {
      salvando.value = false
    }
  }

  return { perfil, carga, salvando, erro, carregar, recarregar, salvar }
}
