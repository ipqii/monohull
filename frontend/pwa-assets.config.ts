import {
  defineConfig,
  minimal2023Preset as preset,
} from '@vite-pwa/assets-generator/config'

export default defineConfig({
  headLinkOptions: { preset: '2023' },
  preset: {
    ...preset,
    transparent: { ...preset.transparent, padding: 0 },
    maskable: { ...preset.maskable, padding: 0.1, resizeOptions: { background: '#6366f1' } },
    apple: { ...preset.apple, padding: 0.1, resizeOptions: { background: '#0a0e1a' } },
  },
  images: ['public/monohull-icon.svg'],
})
