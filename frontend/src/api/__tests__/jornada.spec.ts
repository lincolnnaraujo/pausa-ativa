import { afterEach, describe, expect, it, vi } from 'vitest'

import { problema, umaJornada } from '@/__tests__/fabrica'

import { OperacaoRecusadaError, ServidorIndisponivelError } from '../http'
import {
  buscarJornadaAtual,
  concluirMarco,
  confirmarRecebimento,
  corrigirMarco,
  falharMarco,
  finalizarJornada,
  iniciarJornada,
  type Jornada,
  pausarJornada,
  retomarJornada,
} from '../jornada'

const JORNADA = umaJornada()

function responderCom(resposta: Response) {
  const fetchFalso = vi.fn<typeof fetch>().mockResolvedValue(resposta)
  vi.stubGlobal('fetch', fetchFalso)
  return fetchFalso
}

describe('API da jornada', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('busca a jornada atual', async () => {
    const fetchFalso = responderCom(Response.json(JORNADA))

    await expect(buscarJornadaAtual()).resolves.toEqual(JORNADA)
    expect(fetchFalso).toHaveBeenCalledWith(
      '/api/v1/jornadas/atual',
      expect.objectContaining({ method: 'GET', body: undefined }),
    )
  })

  it('devolve null quando ainda não houve jornada hoje (204)', async () => {
    responderCom(new Response(null, { status: 204 }))

    await expect(buscarJornadaAtual()).resolves.toBeNull()
  })

  it('inicia o dia enviando a meta e a duração do bloco em JSON', async () => {
    const fetchFalso = responderCom(Response.json(JORNADA, { status: 201 }))

    await expect(iniciarJornada(2_500, 10)).resolves.toEqual(JORNADA)
    expect(fetchFalso).toHaveBeenCalledWith(
      '/api/v1/jornadas',
      expect.objectContaining({
        method: 'POST',
        body: '{"metaAguaMl":2500,"duracaoBlocoMin":10}',
        headers: {
          Accept: 'application/json, application/problem+json',
          'Content-Type': 'application/json',
        },
      }),
    )
  })

  it.each<[string, (id: string) => Promise<Jornada>, string]>([
    ['pausar', pausarJornada, '/api/v1/jornadas/j%2F1/pausa'],
    ['retomar', retomarJornada, '/api/v1/jornadas/j%2F1/retomada'],
    ['finalizar', finalizarJornada, '/api/v1/jornadas/j%2F1/finalizacao'],
    ['concluir', concluirMarco, '/api/v1/marcos/j%2F1/conclusao'],
    ['falhar', falharMarco, '/api/v1/marcos/j%2F1/falha'],
  ])('%s faz POST no caminho do recurso, com o id escapado', async (_nome, comando, caminho) => {
    const fetchFalso = responderCom(Response.json(JORNADA))

    await expect(comando('j/1')).resolves.toEqual(JORNADA)
    expect(fetchFalso).toHaveBeenCalledWith(caminho, expect.objectContaining({ method: 'POST' }))
  })

  it('corrige um lembrete enviando a situação certa em JSON', async () => {
    const fetchFalso = responderCom(Response.json(JORNADA))

    await expect(corrigirMarco('marco/1', 'FALHA')).resolves.toEqual(JORNADA)
    expect(fetchFalso).toHaveBeenCalledWith(
      '/api/v1/marcos/marco%2F1/correcao',
      expect.objectContaining({ method: 'POST', body: '{"status":"FALHA"}' }),
    )
  })

  it('confirma o recebimento de um marco (204, sem corpo)', async () => {
    const fetchFalso = responderCom(new Response(null, { status: 204 }))

    await expect(confirmarRecebimento('marco-1')).resolves.toBeUndefined()
    expect(fetchFalso).toHaveBeenCalledWith(
      '/api/v1/marcos/marco-1/recebimento',
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('transforma a recusa do backend em OperacaoRecusadaError com o motivo em português', async () => {
    responderCom(problema(409, 'Não é possível retomar: a jornada está em andamento.'))

    const erro = await retomarJornada('jornada-1').catch((e: unknown) => e)

    expect(erro).toBeInstanceOf(OperacaoRecusadaError)
    expect(erro).toMatchObject({
      status: 409,
      message: 'Não é possível retomar: a jornada está em andamento.',
    })
  })

  it('usa o título quando o Problem Details não traz detalhe', async () => {
    responderCom(
      Response.json(
        { title: 'Bad Request', status: 400 },
        { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    )

    await expect(iniciarJornada(0, 5)).rejects.toThrow(new OperacaoRecusadaError(400, 'Bad Request'))
  })

  it.each([
    ['erro 500, mesmo com Problem Details', problema(500, 'Erro interno')],
    ['4xx sem Problem Details', new Response('Not Found', { status: 404 })],
    [
      'Problem Details ilegível',
      new Response('{', { status: 409, headers: { 'Content-Type': 'application/problem+json' } }),
    ],
    [
      'Problem Details sem mensagem',
      Response.json({ status: 409 }, { status: 409, headers: { 'Content-Type': 'application/problem+json' } }),
    ],
  ])('trata %s como servidor indisponível', async (_caso, resposta) => {
    responderCom(resposta)

    await expect(pausarJornada('jornada-1')).rejects.toBeInstanceOf(ServidorIndisponivelError)
  })
})
