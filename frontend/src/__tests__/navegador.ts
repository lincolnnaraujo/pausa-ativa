import { vi } from 'vitest'

type Ouvinte = (evento: Event) => void

/** EventSource controlado pelo teste: abre, emite eventos e cai quando o teste manda. */
export class EventSourceFalso {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSED = 2
  static instancias: EventSourceFalso[] = []

  static ultima(): EventSourceFalso {
    const ultima = EventSourceFalso.instancias.at(-1)
    if (ultima === undefined) {
      throw new Error('Nenhum EventSource foi criado')
    }
    return ultima
  }

  readyState = EventSourceFalso.CONNECTING
  fechada = false
  private readonly ouvintes = new Map<string, Ouvinte[]>()

  constructor(readonly url: string) {
    EventSourceFalso.instancias.push(this)
  }

  addEventListener(tipo: string, ouvinte: Ouvinte) {
    this.ouvintes.set(tipo, [...(this.ouvintes.get(tipo) ?? []), ouvinte])
  }

  close() {
    this.readyState = EventSourceFalso.CLOSED
    this.fechada = true
  }

  abrir() {
    this.readyState = EventSourceFalso.OPEN
    this.disparar('open', new Event('open'))
  }

  emitir(nome: string, dados: unknown) {
    const texto = typeof dados === 'string' ? dados : JSON.stringify(dados)
    this.disparar(nome, new MessageEvent(nome, { data: texto }))
  }

  /** Queda da conexão. Com `desistir`, o navegador não reconecta sozinho, como no 502 do nginx. */
  cair({ desistir = false } = {}) {
    this.readyState = desistir ? EventSourceFalso.CLOSED : EventSourceFalso.CONNECTING
    this.disparar('error', new Event('error'))
  }

  private disparar(tipo: string, evento: Event) {
    this.ouvintes.get(tipo)?.forEach((ouvinte) => ouvinte(evento))
  }
}

/** Notification do navegador, guardando as notificações criadas. */
export class NotificationFalsa {
  static permission: NotificationPermission = 'granted'
  static requestPermission = vi.fn<() => Promise<NotificationPermission>>()
  static criadas: NotificationFalsa[] = []

  onclick: (() => void) | null = null
  readonly close = vi.fn()

  constructor(
    readonly title: string,
    readonly options: NotificationOptions = {},
  ) {
    NotificationFalsa.criadas.push(this)
  }
}

interface OsciladorFalso {
  type: string
  frequency: { value: number }
  connect: <T>(destino: T) => T
  start: ReturnType<typeof vi.fn>
  stop: ReturnType<typeof vi.fn>
}

/** Web Audio sem som: registra os osciladores tocados. */
export class AudioContextFalso {
  static criados: AudioContextFalso[] = []

  state: AudioContextState = 'running'
  currentTime = 10
  readonly destination = {}
  readonly osciladores: OsciladorFalso[] = []
  readonly resume = vi.fn(async () => {
    this.state = 'running'
  })
  readonly close = vi.fn(async () => {})

  constructor() {
    AudioContextFalso.criados.push(this)
  }

  createOscillator(): OsciladorFalso {
    const oscilador = {
      type: '',
      frequency: { value: 0 },
      connect: <T>(destino: T) => destino,
      start: vi.fn(),
      stop: vi.fn(),
    }
    this.osciladores.push(oscilador)
    return oscilador
  }

  createGain() {
    return {
      gain: { setValueAtTime: vi.fn(), exponentialRampToValueAtTime: vi.fn() },
      connect: <T>(destino: T) => destino,
    }
  }
}

/** Tons tocados em todos os contextos de áudio. */
export function tonsTocados(): OsciladorFalso[] {
  return AudioContextFalso.criados.flatMap((contexto) => contexto.osciladores)
}

interface Opcoes {
  permissao?: NotificationPermission
  /** `navigator.userActivation.hasBeenActive`: falso logo depois de recarregar a página. */
  usuarioJaInteragiu?: boolean
}

/** Troca as APIs do navegador pelos dublês acima. Desfazer com `vi.unstubAllGlobals()`. */
export function instalarNavegadorFalso({ permissao = 'granted', usuarioJaInteragiu = true }: Opcoes = {}) {
  EventSourceFalso.instancias = []
  NotificationFalsa.permission = permissao
  NotificationFalsa.criadas = []
  NotificationFalsa.requestPermission = vi.fn(async () => NotificationFalsa.permission)
  AudioContextFalso.criados = []
  vi.stubGlobal('EventSource', EventSourceFalso)
  vi.stubGlobal('Notification', NotificationFalsa)
  vi.stubGlobal('AudioContext', AudioContextFalso)
  Object.defineProperty(navigator, 'userActivation', {
    value: { hasBeenActive: usuarioJaInteragiu },
    configurable: true,
  })
}
