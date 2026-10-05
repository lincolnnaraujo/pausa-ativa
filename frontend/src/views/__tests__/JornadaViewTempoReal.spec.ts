import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { comMarco, umaJornada } from '@/__tests__/fabrica'
import {
  EventSourceFalso,
  instalarNavegadorFalso,
  NotificationFalsa,
  tonsTocados,
} from '@/__tests__/navegador'
import { buscarJornadaAtual, confirmarRecebimento, iniciarJornada, type Jornada } from '@/api/jornada'
import { buscarStatus } from '@/api/sistema'

import JornadaView from '../JornadaView.vue'

vi.mock('@/api/jornada', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/jornada')>()),
  buscarJornadaAtual: vi.fn(),
  iniciarJornada: vi.fn(),
  confirmarRecebimento: vi.fn(),
}))

vi.mock('@/api/sistema', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sistema')>()),
  buscarStatus: vi.fn(),
}))

vi.mock('@/api/perfil', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/perfil')>()),
  buscarPerfil: vi.fn().mockResolvedValue({
    articulacoesPoupadas: [],
    nivel: 'INICIANTE',
    equipamentos: [],
    aceitaChao: true,
  }),
}))

enableAutoUnmount(afterEach)

/** 2 h trabalhadas às 11:00, sem lembrete pendente. */
const EM_ANDAMENTO = umaJornada({
  tempoTrabalhadoSegundos: 7_200,
  calculadoEm: '2026-10-02T11:00:00-03:00',
})

/** O marco 4 acabou de disparar e ninguém confirmou o recebimento. */
const COM_PENDENTE: Jornada = {
  ...comMarco(EM_ANDAMENTO, 4, { status: 'PENDENTE', disparadoEm: '2026-10-02T11:00:00-03:00' }),
  calculadoEm: '2026-10-02T11:00:01-03:00',
}
const MARCO_4 = COM_PENDENTE.marcos[3]!

async function montar(jornada: Jornada | null) {
  vi.mocked(buscarJornadaAtual).mockResolvedValue(jornada)
  const wrapper = mount(JornadaView)
  await flushPromises()
  return wrapper
}

/** Abre a conexão SSE da tela e espera a sincronização que vem com ela. */
async function conectar() {
  EventSourceFalso.ultima().abrir()
  await flushPromises()
}

function existe(wrapper: VueWrapper, testid: string) {
  return wrapper.find(`[data-testid="${testid}"]`).exists()
}

function botao(wrapper: VueWrapper, texto: string) {
  const encontrado = wrapper.findAll('button').find((b) => b.text() === texto)
  if (encontrado === undefined) {
    throw new Error(`Botão "${texto}" não está na tela`)
  }
  return encontrado
}

describe('JornadaView em tempo real', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    instalarNavegadorFalso()
    vi.mocked(confirmarRecebimento).mockResolvedValue()
    vi.mocked(buscarStatus).mockResolvedValue({
      aplicacao: 'pausa-ativa',
      versao: '0.2.0',
      agora: '2026-10-02T14:00:00Z',
      fuso: 'America/Sao_Paulo',
    })
    localStorage.clear()
  })

  afterEach(() => vi.unstubAllGlobals())

  describe('lembrete disparado (Cenário 1)', () => {
    it('confirma o recebimento, notifica, toca o som e mostra o cartão', async () => {
      const wrapper = await montar(EM_ANDAMENTO)
      await conectar()
      const fonte = EventSourceFalso.ultima()

      fonte.emitir('marco-disparado', MARCO_4)
      fonte.emitir('jornada-atualizada', COM_PENDENTE)
      await flushPromises()

      expect(confirmarRecebimento).toHaveBeenCalledExactlyOnceWith('marco-4')
      expect(NotificationFalsa.criadas).toHaveLength(1)
      expect(NotificationFalsa.criadas[0]!.title).toBe('Hora da água 💧')
      expect(NotificationFalsa.criadas[0]!.options).toEqual({ body: 'Beba ~190 ml.', tag: 'marco-4' })
      expect(tonsTocados()).toHaveLength(1)
      expect(wrapper.get('[data-testid="marco-pendente"]').text()).toContain('Lembrete 4 de 16')
    })

    it('com a permissão negada, confirma o recebimento e mostra o cartão mesmo assim', async () => {
      instalarNavegadorFalso({ permissao: 'denied' })
      const wrapper = await montar(EM_ANDAMENTO)

      EventSourceFalso.ultima().emitir('marco-disparado', MARCO_4)
      EventSourceFalso.ultima().emitir('jornada-atualizada', COM_PENDENTE)
      await flushPromises()

      expect(confirmarRecebimento).toHaveBeenCalledWith('marco-4')
      expect(NotificationFalsa.criadas).toHaveLength(0)
      expect(existe(wrapper, 'marco-pendente')).toBe(true)
    })

    it('com o som desligado, só notifica', async () => {
      const wrapper = await montar(EM_ANDAMENTO)
      await wrapper.get('[data-testid="som"]').setValue(false)
      const amostras = tonsTocados().length

      EventSourceFalso.ultima().emitir('marco-disparado', MARCO_4)

      expect(localStorage.getItem('pausa-ativa.som')).toBe('desligado')
      expect(NotificationFalsa.criadas).toHaveLength(1)
      expect(tonsTocados()).toHaveLength(amostras)
    })

    it('se a confirmação falhar, tenta de novo na próxima atualização', async () => {
      vi.mocked(confirmarRecebimento).mockRejectedValueOnce(new Error('rede'))
      await montar(EM_ANDAMENTO)
      const fonte = EventSourceFalso.ultima()

      fonte.emitir('marco-disparado', MARCO_4)
      await flushPromises()
      fonte.emitir('jornada-atualizada', COM_PENDENTE)
      await flushPromises()

      expect(confirmarRecebimento).toHaveBeenCalledTimes(2)
    })

    it('não confirma o que outra aba já confirmou', async () => {
      await montar(EM_ANDAMENTO)

      EventSourceFalso.ultima().emitir(
        'jornada-atualizada',
        comMarco(COM_PENDENTE, 4, { recebidoEm: '2026-10-02T11:00:01-03:00' }),
      )
      await flushPromises()

      expect(confirmarRecebimento).not.toHaveBeenCalled()
    })
  })

  it('ao abrir a página com um lembrete pendente, confirma o recebimento sem notificar', async () => {
    const wrapper = await montar(COM_PENDENTE)

    expect(existe(wrapper, 'marco-pendente')).toBe(true)
    expect(confirmarRecebimento).toHaveBeenCalledExactlyOnceWith('marco-4')
    expect(NotificationFalsa.criadas).toHaveLength(0)
  })

  it('quando a conexão abre, busca a situação real: eventos podem ter passado antes dela', async () => {
    const wrapper = await montar(EM_ANDAMENTO)
    vi.mocked(buscarJornadaAtual).mockResolvedValue(COM_PENDENTE)

    await conectar()

    expect(buscarJornadaAtual).toHaveBeenCalledTimes(2)
    expect(existe(wrapper, 'marco-pendente')).toBe(true)
  })

  describe('reconexão', () => {
    it('na queda, avisa e desabilita os botões até a conexão voltar', async () => {
      const wrapper = await montar(EM_ANDAMENTO)
      await conectar()

      EventSourceFalso.ultima().cair()
      await flushPromises()

      expect(existe(wrapper, 'reconectando')).toBe(true)
      expect(botao(wrapper, 'Pausar').attributes('disabled')).toBeDefined()
      expect(botao(wrapper, 'Finalizar dia').attributes('disabled')).toBeDefined()
      expect(wrapper.get('[data-testid="status-servidor"]').text()).toContain('Reconectando…')

      await conectar()

      expect(existe(wrapper, 'reconectando')).toBe(false)
      expect(botao(wrapper, 'Pausar').attributes('disabled')).toBeUndefined()
    })

    it('ao voltar, recupera o lembrete perdido na queda, confirma e anuncia', async () => {
      const wrapper = await montar(EM_ANDAMENTO)
      await conectar()
      EventSourceFalso.ultima().cair()
      vi.mocked(buscarJornadaAtual).mockResolvedValue(COM_PENDENTE)

      await conectar()

      expect(existe(wrapper, 'marco-pendente')).toBe(true)
      expect(confirmarRecebimento).toHaveBeenCalledExactlyOnceWith('marco-4')
      expect(NotificationFalsa.criadas.map((n) => n.options.tag)).toEqual(['marco-4'])
    })

    it('não anuncia de novo um lembrete já anunciado por esta aba', async () => {
      await montar(EM_ANDAMENTO)
      await conectar()
      EventSourceFalso.ultima().emitir('marco-disparado', MARCO_4)
      EventSourceFalso.ultima().cair()
      vi.mocked(buscarJornadaAtual).mockResolvedValue(COM_PENDENTE)

      await conectar()

      expect(NotificationFalsa.criadas).toHaveLength(1)
    })

    it('com a página aberta sem jornada, a reconexão mostra a jornada iniciada em outra aba', async () => {
      vi.mocked(buscarJornadaAtual).mockRejectedValueOnce(new Error('fora'))
      const wrapper = mount(JornadaView)
      await flushPromises()
      expect(existe(wrapper, 'jornada-indisponivel')).toBe(true)
      vi.mocked(buscarJornadaAtual).mockResolvedValue(EM_ANDAMENTO)

      await conectar()

      expect(existe(wrapper, 'painel')).toBe(true)
    })
  })

  describe('permissão de notificação', () => {
    it('iniciar o dia pede a permissão, dentro do clique', async () => {
      instalarNavegadorFalso({ permissao: 'default' })
      vi.mocked(iniciarJornada).mockResolvedValue(umaJornada())
      const wrapper = await montar(null)

      await wrapper.get('form').trigger('submit')
      await flushPromises()

      expect(NotificationFalsa.requestPermission).toHaveBeenCalledOnce()
      expect(iniciarJornada).toHaveBeenCalledWith(3_000, 5)
    })

    it('não concedida: o aviso fixo tem um botão que pede a permissão e some quando ela é dada', async () => {
      instalarNavegadorFalso({ permissao: 'default' })
      NotificationFalsa.requestPermission.mockResolvedValue('granted')
      const wrapper = await montar(EM_ANDAMENTO)
      expect(wrapper.get('[data-testid="aviso-permissao"]').attributes('data-permissao')).toBe('default')

      await botao(wrapper, 'Permitir notificações').trigger('click')
      await flushPromises()

      expect(existe(wrapper, 'aviso-permissao')).toBe(false)
    })

    it('negada: o aviso fixo explica como liberar no Chrome', async () => {
      instalarNavegadorFalso({ permissao: 'denied' })

      const wrapper = await montar(null)

      const aviso = wrapper.get('[data-testid="aviso-permissao"]')
      expect(aviso.attributes('data-permissao')).toBe('denied')
      expect(aviso.text()).toContain('As notificações estão bloqueadas para este site.')
      expect(aviso.text()).toContain('clique no ícone à esquerda do endereço')
    })

    it('navegador sem notificações: orienta a manter a aba à vista', async () => {
      instalarNavegadorFalso()
      vi.stubGlobal('Notification', undefined)

      const wrapper = await montar(null)

      expect(wrapper.get('[data-testid="aviso-permissao"]').text()).toContain('Mantenha a aba')
    })

    it('concedida: nenhum aviso', async () => {
      const wrapper = await montar(EM_ANDAMENTO)

      expect(existe(wrapper, 'aviso-permissao')).toBe(false)
    })
  })

  describe('som bloqueado (D7)', () => {
    it('com a jornada aberta, avisa até o primeiro clique na página', async () => {
      instalarNavegadorFalso({ usuarioJaInteragiu: false })
      const wrapper = await montar(EM_ANDAMENTO)
      expect(existe(wrapper, 'aviso-som')).toBe(true)

      document.dispatchEvent(new Event('pointerdown'))
      await flushPromises()

      expect(existe(wrapper, 'aviso-som')).toBe(false)
    })

    it('sem jornada aberta, não há lembrete para tocar: sem aviso', async () => {
      instalarNavegadorFalso({ usuarioJaInteragiu: false })

      const wrapper = await montar(null)

      expect(existe(wrapper, 'aviso-som')).toBe(false)
    })
  })

  it('o rodapé mostra a conexão e a versão do backend', async () => {
    const wrapper = await montar(EM_ANDAMENTO)
    expect(wrapper.get('[data-testid="status-servidor"]').text()).toBe('Conectando ao servidor…')

    await conectar()

    expect(wrapper.get('[data-testid="status-servidor"]').text()).toBe(
      'Conectado ao servidor · versão 0.2.0',
    )
  })
})
