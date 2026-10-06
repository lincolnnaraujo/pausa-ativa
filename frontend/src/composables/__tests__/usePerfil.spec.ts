import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ServidorIndisponivelError } from '@/api/http'
import { buscarPerfil, type Perfil, salvarPerfil } from '@/api/perfil'

import { usePerfil } from '../usePerfil'

vi.mock('@/api/perfil', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/perfil')>()),
  buscarPerfil: vi.fn(),
  salvarPerfil: vi.fn(),
}))

const PERFIL: Perfil = { articulacoesPoupadas: [], nivel: 'INICIANTE', equipamentos: [], aceitaChao: true }

describe('usePerfil', () => {
  beforeEach(() => vi.resetAllMocks())

  it('um segundo Salvar enquanto o primeiro não volta é ignorado', async () => {
    let responder: (perfil: Perfil) => void = () => {}
    vi.mocked(salvarPerfil).mockReturnValue(new Promise((resolve) => (responder = resolve)))
    const tela = usePerfil()

    const primeiro = tela.salvar(PERFIL)
    const segundo = await tela.salvar(PERFIL)
    responder(PERFIL)

    expect(segundo).toBe(false)
    expect(await primeiro).toBe(true)
    expect(salvarPerfil).toHaveBeenCalledTimes(1)
    expect(tela.perfil.value).toEqual(PERFIL)
  })

  it('uma falha ao buscar de novo não troca a tela pronta por indisponível', async () => {
    vi.mocked(buscarPerfil)
      .mockResolvedValueOnce(PERFIL)
      .mockRejectedValueOnce(new ServidorIndisponivelError('fora'))
    const tela = usePerfil()
    await tela.carregar()

    await tela.recarregar()

    expect(tela.carga.value).toBe('pronta')
    expect(tela.perfil.value).toEqual(PERFIL)
  })
})
