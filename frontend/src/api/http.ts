export const TEMPO_LIMITE_MS = 5_000

/** O backend não respondeu a tempo, a rede falhou ou a resposta não foi tratável. */
export class ServidorIndisponivelError extends Error {
  override name = 'ServidorIndisponivelError'
}

/**
 * O backend recusou a operação (4xx com Problem Details, RFC 9457). A mensagem é o `detail`, em
 * português, pronta para a tela.
 */
export class OperacaoRecusadaError extends Error {
  override name = 'OperacaoRecusadaError'

  constructor(
    readonly status: number,
    detalhe: string,
  ) {
    super(detalhe)
  }
}

interface Opcoes {
  corpo?: unknown
  tempoLimiteMs?: number
}

/** Faz a requisição e devolve a resposta 2xx; qualquer outra vira um dos erros acima. */
export async function requisitar(
  metodo: 'GET' | 'POST' | 'PUT',
  caminho: string,
  { corpo, tempoLimiteMs = TEMPO_LIMITE_MS }: Opcoes = {},
): Promise<Response> {
  const controle = new AbortController()
  const temporizador = setTimeout(() => controle.abort(), tempoLimiteMs)

  try {
    const resposta = await fetch(caminho, {
      method: metodo,
      headers: {
        Accept: 'application/json, application/problem+json',
        ...(corpo !== undefined && { 'Content-Type': 'application/json' }),
      },
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
      signal: controle.signal,
    })
    if (!resposta.ok) {
      throw await erroDaResposta(resposta)
    }
    return resposta
  } catch (erro) {
    if (erro instanceof ServidorIndisponivelError || erro instanceof OperacaoRecusadaError) {
      throw erro
    }
    throw new ServidorIndisponivelError('O servidor não respondeu', { cause: erro })
  } finally {
    clearTimeout(temporizador)
  }
}

/** 4xx com Problem Details é recusa de negócio; o resto (5xx, 502 do nginx) é servidor fora. */
async function erroDaResposta(resposta: Response): Promise<Error> {
  const problema = resposta.status < 500 ? await lerProblema(resposta) : undefined
  if (problema === undefined) {
    return new ServidorIndisponivelError(`O servidor respondeu ${resposta.status}`)
  }
  return new OperacaoRecusadaError(resposta.status, problema)
}

async function lerProblema(resposta: Response): Promise<string | undefined> {
  if (!resposta.headers.get('Content-Type')?.includes('json')) {
    return undefined
  }
  try {
    const corpo = (await resposta.json()) as { detail?: unknown; title?: unknown }
    const mensagem = corpo.detail ?? corpo.title
    return typeof mensagem === 'string' ? mensagem : undefined
  } catch {
    return undefined
  }
}
