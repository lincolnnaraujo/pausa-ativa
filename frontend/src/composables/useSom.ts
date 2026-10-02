import { computed, onScopeDispose, ref, watch } from 'vue'

import { guardarSomLigado, lerSomLigado } from '@/preferencias'

const FREQUENCIA_HZ = 880
const DURACAO_S = 0.35
const VOLUME = 0.2

const GESTOS = ['pointerdown', 'keydown'] as const

/**
 * Tom curto dos lembretes, gerado pelo navegador com Web Audio, sem arquivo de áudio (spec H2, D7).
 * O Chrome só libera o som depois que a pessoa interage com a página; até lá, `bloqueado` avisa.
 */
export function useSom() {
  const ligado = ref(lerSomLigado())
  const liberado = ref(navigator.userActivation?.hasBeenActive ?? true)
  const bloqueado = computed(() => ligado.value && !liberado.value)

  let contexto: AudioContext | null = null

  function liberar() {
    liberado.value = true
    pararDeEsperarGesto()
  }
  function pararDeEsperarGesto() {
    GESTOS.forEach((gesto) => document.removeEventListener(gesto, liberar, true))
  }
  if (!liberado.value) {
    GESTOS.forEach((gesto) => document.addEventListener(gesto, liberar, true))
  }

  onScopeDispose(() => {
    pararDeEsperarGesto()
    void contexto?.close().catch(() => {})
  })

  // Ligar o som toca uma amostra: confirma que funciona, e o clique já libera o áudio.
  watch(ligado, (valor) => {
    guardarSomLigado(valor)
    if (valor) {
      tocar()
    }
  })

  function tocar() {
    // Bloqueado, o tom ficaria preso no contexto suspenso e tocaria atrasado no primeiro clique.
    if (!ligado.value || !liberado.value) {
      return
    }
    try {
      contexto ??= new AudioContext()
      if (contexto.state === 'suspended') {
        void contexto.resume()
      }
      const inicio = contexto.currentTime
      const oscilador = contexto.createOscillator()
      const volume = contexto.createGain()
      oscilador.type = 'sine'
      oscilador.frequency.value = FREQUENCIA_HZ
      volume.gain.setValueAtTime(0.0001, inicio)
      volume.gain.exponentialRampToValueAtTime(VOLUME, inicio + 0.02)
      volume.gain.exponentialRampToValueAtTime(0.0001, inicio + DURACAO_S)
      oscilador.connect(volume).connect(contexto.destination)
      oscilador.start(inicio)
      oscilador.stop(inicio + DURACAO_S)
    } catch {
      // Sem Web Audio, ficam a notificação e o cartão na página.
    }
  }

  return { ligado, bloqueado, tocar }
}
