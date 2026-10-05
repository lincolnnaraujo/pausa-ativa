import { afterEach, describe, expect, it, vi } from 'vitest'

import { problema } from '@/__tests__/fabrica'

import { OperacaoRecusadaError } from '../http'
import { buscarPerfil, type Perfil, salvarPerfil } from '../perfil'

const PERFIL: Perfil = {
  articulacoesPoupadas: ['JOELHO'],
  nivel: 'INTERMEDIARIO',
  equipamentos: ['HALTERES_2KG'],
  aceitaChao: false,
}

function responderCom(resposta: Response) {
  const fetchFalso = vi.fn<typeof fetch>().mockResolvedValue(resposta)
  vi.stubGlobal('fetch', fetchFalso)
  return fetchFalso
}

describe('API do perfil', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('busca o perfil', async () => {
    const fetchFalso = responderCom(Response.json(PERFIL))

    await expect(buscarPerfil()).resolves.toEqual(PERFIL)
    expect(fetchFalso).toHaveBeenCalledWith('/api/v1/perfil', expect.objectContaining({ method: 'GET' }))
  })

  it('devolve null enquanto o perfil não foi preenchido (204)', async () => {
    responderCom(new Response(null, { status: 204 }))

    await expect(buscarPerfil()).resolves.toBeNull()
  })

  it('grava o perfil inteiro com PUT', async () => {
    const fetchFalso = responderCom(Response.json(PERFIL))

    await expect(salvarPerfil(PERFIL)).resolves.toEqual(PERFIL)
    expect(fetchFalso).toHaveBeenCalledWith(
      '/api/v1/perfil',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify(PERFIL),
      }),
    )
  })

  it('perfil recusado vira erro com o motivo do servidor', async () => {
    responderCom(problema(400, 'Nível inválido.'))

    await expect(salvarPerfil(PERFIL)).rejects.toThrow(new OperacaoRecusadaError(400, 'Nível inválido.'))
  })
})
