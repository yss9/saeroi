import react from '@vitejs/plugin-react'
import { defineConfig, type UserConfig } from 'vite'
import type { InlineConfig } from 'vitest'

const config = {
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
} satisfies UserConfig & { test: InlineConfig }

// https://vite.dev/config/
export default defineConfig(config)
