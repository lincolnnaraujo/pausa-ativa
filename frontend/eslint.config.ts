import pluginVitest from '@vitest/eslint-plugin'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import { globalIgnores } from 'eslint/config'
import pluginVue from 'eslint-plugin-vue'

export default defineConfigWithVueTs(
  {
    name: 'app/arquivos',
    files: ['**/*.{vue,ts,mts,tsx}'],
  },

  // contrato.ts é gerado do OpenAPI (npm run contrato).
  globalIgnores(['**/dist/**', '**/coverage/**', 'src/api/contrato.ts']),

  ...pluginVue.configs['flat/recommended'],
  vueTsConfigs.recommended,

  {
    ...pluginVitest.configs.recommended,
    files: ['src/**/__tests__/*'],
  },
)
