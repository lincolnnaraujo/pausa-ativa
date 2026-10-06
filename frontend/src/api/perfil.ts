import type { components } from './contrato'
import { requisitar } from './http'

/** Perfil físico (spec H3, seção 3.1). Um só, porque o usuário é único. */
export type Perfil = components['schemas']['Perfil']
export type Articulacao = Perfil['articulacoesPoupadas'][number]
export type Equipamento = Perfil['equipamentos'][number]
export type Nivel = Perfil['nivel']

/** Os padrões do formulário quando ainda não há perfil (spec H3, seção 3.1). */
export const PERFIL_PADRAO: Perfil = {
  articulacoesPoupadas: [],
  nivel: 'INICIANTE',
  equipamentos: [],
  aceitaChao: true,
}

/** O perfil atual; `null` enquanto o usuário não preencheu (204). */
export async function buscarPerfil(): Promise<Perfil | null> {
  const resposta = await requisitar('GET', '/api/v1/perfil')
  return resposta.status === 204 ? null : ((await resposta.json()) as Perfil)
}

/** Cria ou substitui o perfil. Vale para os blocos montados daqui em diante. */
export async function salvarPerfil(perfil: Perfil): Promise<Perfil> {
  const resposta = await requisitar('PUT', '/api/v1/perfil', { corpo: perfil })
  return (await resposta.json()) as Perfil
}
