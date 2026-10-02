import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'

import { umMarco } from '@/__tests__/fabrica'
import { instalarNavegadorFalso, NotificationFalsa } from '@/__tests__/navegador'

import { useNotificacoes } from '../useNotificacoes'

let escopo: ReturnType<typeof effectScope>
function criar() {
  escopo = effectScope()
  return escopo.run(useNotificacoes)!
}

const MARCO = umMarco(1, { status: 'PENDENTE' })

/** API de permissões do Chrome, que avisa quando a pessoa muda a permissão do site. */
function instalarApiDePermissoes(query: () => Promise<PermissionStatus>) {
  Object.defineProperty(navigator, 'permissions', { value: { query }, configurable: true })
}

describe('useNotificacoes', () => {
  beforeEach(() => instalarNavegadorFalso())

  afterEach(() => {
    escopo.stop()
    vi.unstubAllGlobals()
    Reflect.deleteProperty(navigator, 'permissions')
  })

  it.each<NotificationPermission>(['granted', 'denied', 'default'])('lê a permissão atual: %s', (permissao) => {
    NotificationFalsa.permission = permissao

    expect(criar().permissao.value).toBe(permissao)
  })

  it('sem a API de notificações, informa indisponível e não quebra', async () => {
    vi.stubGlobal('Notification', undefined)
    const { permissao, pedirPermissao, notificar } = criar()

    expect(permissao.value).toBe('indisponivel')
    await pedirPermissao()
    notificar(MARCO)
  })

  it('pede a permissão só quando ainda não foi decidida', async () => {
    NotificationFalsa.permission = 'default'
    const { permissao, pedirPermissao } = criar()
    NotificationFalsa.requestPermission.mockResolvedValue('granted')

    await pedirPermissao()
    expect(permissao.value).toBe('granted')

    await pedirPermissao()
    expect(NotificationFalsa.requestPermission).toHaveBeenCalledTimes(1)
  })

  it('se o pedido falhar, relê a permissão', async () => {
    NotificationFalsa.permission = 'default'
    const { permissao, pedirPermissao } = criar()
    NotificationFalsa.requestPermission.mockRejectedValue(new TypeError('sem gesto'))
    NotificationFalsa.permission = 'denied'

    await pedirPermissao()

    expect(permissao.value).toBe('denied')
  })

  it('notifica com o título do lembrete, a mensagem e o id do marco como tag', () => {
    criar().notificar(MARCO)

    expect(NotificationFalsa.criadas).toHaveLength(1)
    const [notificacao] = NotificationFalsa.criadas
    expect(notificacao!.title).toBe('Hora da água 💧')
    expect(notificacao!.options).toEqual({
      body: 'Beba ~190 ml. Levante-se para buscar a água.',
      tag: 'marco-1',
    })
  })

  it('clicar na notificação traz a aba para a frente e fecha a notificação', () => {
    const focar = vi.spyOn(window, 'focus').mockImplementation(() => {})
    criar().notificar(MARCO)

    NotificationFalsa.criadas[0]!.onclick?.()

    expect(focar).toHaveBeenCalled()
    expect(NotificationFalsa.criadas[0]!.close).toHaveBeenCalled()
    focar.mockRestore()
  })

  it.each<NotificationPermission>(['denied', 'default'])('sem permissão (%s), não notifica', (permissao) => {
    NotificationFalsa.permission = permissao

    criar().notificar(MARCO)

    expect(NotificationFalsa.criadas).toHaveLength(0)
  })

  it('se o navegador recusar o construtor, segue sem notificação', () => {
    vi.stubGlobal(
      'Notification',
      Object.assign(
        function () {
          throw new TypeError('Illegal constructor')
        },
        { permission: 'granted' },
      ),
    )

    expect(() => criar().notificar(MARCO)).not.toThrow()
  })

  it('acompanha a mudança de permissão feita nas configurações do site', async () => {
    NotificationFalsa.permission = 'denied'
    const status = new EventTarget() as PermissionStatus
    instalarApiDePermissoes(async () => status)
    const { permissao } = criar()
    await flushPromises()

    NotificationFalsa.permission = 'granted'
    status.dispatchEvent(new Event('change'))
    expect(permissao.value).toBe('granted')

    escopo.stop()
    NotificationFalsa.permission = 'denied'
    status.dispatchEvent(new Event('change'))
    expect(permissao.value).toBe('granted')
  })

  it('não acompanha a permissão se a tela já foi desmontada quando a consulta voltou', async () => {
    const status = new EventTarget() as PermissionStatus
    const ouvir = vi.spyOn(status, 'addEventListener')
    instalarApiDePermissoes(async () => status)
    criar()

    escopo.stop()
    await flushPromises()

    expect(ouvir).not.toHaveBeenCalled()
  })

  it('sem resposta da API de permissões, fica com a permissão lida no início', async () => {
    instalarApiDePermissoes(() => Promise.reject(new TypeError('não suportado')))
    const { permissao } = criar()
    await flushPromises()

    expect(permissao.value).toBe('granted')
  })
})
