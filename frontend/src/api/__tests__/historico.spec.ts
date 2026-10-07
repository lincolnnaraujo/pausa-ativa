import { afterEach, describe, expect, it, vi } from 'vitest'

import { problema, umaJornada, umaSemana } from '@/__tests__/fabrica'

import { buscarHistorico } from '../historico'
import { OperacaoRecusadaError } from '../http'
import { buscarJornadaDoDia } from '../jornada'

function responderCom(resposta: Response) {
  const fetchFalso = vi.fn<typeof fetch>().mockResolvedValue(resposta)
  vi.stubGlobal('fetch', fetchFalso)
  return fetchFalso
}

describe('API do histórico', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('busca o período que contém a data', async () => {
    const semana = umaSemana()
    const fetchFalso = responderCom(Response.json(semana))

    await expect(buscarHistorico('SEMANA', '2026-10-07')).resolves.toEqual(semana)
    expect(fetchFalso).toHaveBeenCalledWith(
      '/api/v1/historico?periodo=SEMANA&data=2026-10-07',
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('a data futura volta como recusa, com o motivo do backend', async () => {
    responderCom(problema(400, 'O histórico vai até hoje: 2026-10-08 ainda não chegou.'))

    await expect(buscarHistorico('DIA', '2026-10-08')).rejects.toThrow(
      new OperacaoRecusadaError(400, 'O histórico vai até hoje: 2026-10-08 ainda não chegou.'),
    )
  })

  it('busca a jornada de um dia, com os blocos', async () => {
    const jornada = umaJornada({ dataReferencia: '2026-10-05' })
    const fetchFalso = responderCom(Response.json(jornada))

    await expect(buscarJornadaDoDia('2026-10-05')).resolves.toEqual(jornada)
    expect(fetchFalso).toHaveBeenCalledWith(
      '/api/v1/jornadas?data=2026-10-05',
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('devolve null quando não houve jornada no dia (204)', async () => {
    responderCom(new Response(null, { status: 204 }))

    await expect(buscarJornadaDoDia('2026-10-04')).resolves.toBeNull()
  })
})
