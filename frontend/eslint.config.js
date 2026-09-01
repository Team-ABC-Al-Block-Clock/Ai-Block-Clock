import js from '@eslint/js'
import { defineConfig, globalIgnores } from 'eslint/config'
import configPrettier from 'eslint-config-prettier'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import globals from 'globals'
import tseslint from 'typescript-eslint'

export default defineConfig([
  // html/ 은 디자인 시안(프로토타입)이라 린트 대상에서 제외한다.
  globalIgnores(['dist', 'html']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
      // Prettier 와 충돌하는 포맷 관련 룰을 끈다. 반드시 마지막에 위치할 것.
      configPrettier,
    ],
    languageOptions: {
      globals: globals.browser,
    },
  },
])
