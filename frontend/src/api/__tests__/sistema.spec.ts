import { afterEach, describe, expect, it, vi } from 'vitest'

import {
  buscarStatus,
  ServidorIndisponivelError,
  type StatusDoSistema,
  TEMPO_LIMITE_MS,
} from '../sistema'

const STATUS: StatusDoSistema = {
  aplicacao: 'pausa-ativa',
  versao: '0.1.0',
  agora: '2026-10-01T09:00:00-03:00',
  fuso: 'America/Sao_Paulo',
}

function responderCom(resposta: Response) {
  const fetchFalso = vi.fn<typeof fetch>().mockResolvedValue(resposta)
  vi.stubGlobal('fetch', fetchFalso)
  return fetchFalso
}

describe('buscarStatus', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('devolve o status quando o backend responde 200', async () => {
    const fetchFalso = responderCom(Response.json(STATUS))

    await expect(buscarStatus()).resolves.toEqual(STATUS)
    expect(fetchFalso).toHaveBeenCalledWith(
      '/api/v1/sistema/status',
      expect.objectContaining({ headers: { Accept: 'application/json' } }),
    )
  })

  it('trata resposta diferente de 200 como servidor indisponível', async () => {
    responderCom(new Response('Bad Gateway', { status: 502 }))

    await expect(buscarStatus()).rejects.toThrow(
      new ServidorIndisponivelError('O servidor respondeu 502'),
    )
  })

  it('trata erro de rede como servidor indisponível, guardando a causa', async () => {
    const falhaDeRede = new TypeError('Failed to fetch')
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockRejectedValue(falhaDeRede))

    const erro = await buscarStatus().catch((e: unknown) => e)

    expect(erro).toBeInstanceOf(ServidorIndisponivelError)
    expect((erro as Error).cause).toBe(falhaDeRede)
  })

  it(`desiste depois de ${TEMPO_LIMITE_MS} ms sem resposta`, async () => {
    vi.useFakeTimers()
    // Simula um backend que nunca responde: a promessa só termina quando o sinal é abortado.
    const fetchFalso = vi.fn<typeof fetch>(
      (_url, init) =>
        new Promise((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () =>
            reject(new DOMException('This operation was aborted', 'AbortError')),
          )
        }),
    )
    vi.stubGlobal('fetch', fetchFalso)

    const resultado = buscarStatus().catch((e: unknown) => e)

    await vi.advanceTimersByTimeAsync(TEMPO_LIMITE_MS - 1)
    expect(fetchFalso.mock.calls[0]?.[1]?.signal?.aborted).toBe(false)

    await vi.advanceTimersByTimeAsync(1)
    expect(await resultado).toBeInstanceOf(ServidorIndisponivelError)
  })
})
