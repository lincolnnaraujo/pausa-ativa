import { fileURLToPath } from 'node:url'

import { configDefaults, defineConfig, mergeConfig } from 'vitest/config'

import viteConfig from './vite.config.ts'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      exclude: [...configDefaults.exclude],
      root: fileURLToPath(new URL('./', import.meta.url)),
      coverage: {
        provider: 'v8',
        include: ['src/**/*.{ts,vue}'],
        // main.ts só monta a aplicação, como o PausaAtivaApplication no backend.
        // contrato.ts só tem tipos gerados do OpenAPI.
        exclude: ['src/main.ts', 'src/api/contrato.ts', 'src/**/__tests__/**'],
        reporter: ['text', 'html', 'lcov'],
        // DoD: abaixo de 80% de linhas o npm test falha.
        thresholds: { lines: 80 },
      },
    },
  }),
)
