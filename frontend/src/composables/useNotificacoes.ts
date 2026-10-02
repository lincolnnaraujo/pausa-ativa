import { onScopeDispose, ref } from 'vue'

import type { Marco } from '@/api/jornada'
import { tituloDoMarco } from '@/formatacao'

/** `indisponivel`: o navegador não tem a API de notificações. */
export type PermissaoDeNotificacao = NotificationPermission | 'indisponivel'

/**
 * Notificações do Chrome para os lembretes (spec H2, seção 8). Os botões Concluir e Falhar ficam na
 * página; clicar na notificação traz a aba para a frente (D6).
 */
export function useNotificacoes() {
  const permissao = ref<PermissaoDeNotificacao>(lerPermissao())
  const atualizar = () => (permissao.value = lerPermissao())

  // A pessoa pode liberar as notificações nas configurações do site com a página aberta; o aviso
  // fixo some sem precisar recarregar.
  let encerrado = false
  let status: PermissionStatus | undefined
  navigator.permissions
    ?.query({ name: 'notifications' })
    .then((resultado) => {
      if (!encerrado) {
        status = resultado
        status.addEventListener('change', atualizar)
      }
    })
    .catch(() => {
      // Sem a API de permissões, o aviso se atualiza ao recarregar a página.
    })
  onScopeDispose(() => {
    encerrado = true
    status?.removeEventListener('change', atualizar)
  })

  /** Pede a permissão uma vez. O Chrome só mostra o pedido durante um clique da pessoa. */
  async function pedirPermissao() {
    if (permissao.value !== 'default') {
      return
    }
    try {
      permissao.value = await Notification.requestPermission()
    } catch {
      atualizar()
    }
  }

  /** A `tag` é o id do marco: com várias abas abertas, o Chrome mostra uma notificação só. */
  function notificar(marco: Marco) {
    if (permissao.value !== 'granted') {
      return
    }
    try {
      const notificacao = new Notification(tituloDoMarco(marco.categoria), {
        body: marco.mensagem,
        tag: marco.id,
      })
      notificacao.onclick = () => {
        window.focus()
        notificacao.close()
      }
    } catch {
      // Navegadores que exigem service worker recusam o construtor; o cartão na página continua.
    }
  }

  return { permissao, pedirPermissao, notificar }
}

function lerPermissao(): PermissaoDeNotificacao {
  return typeof Notification === 'undefined' ? 'indisponivel' : Notification.permission
}
