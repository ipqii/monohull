import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// Version shown in the app footer. APP_VERSION is set by the Maven build from the
// project version; npm_package_version is the fallback for a plain `npm run dev`/build.
const appVersion = process.env.APP_VERSION || process.env.npm_package_version || '0.0.0'

export default defineConfig({
  define: {
    __APP_VERSION__: JSON.stringify(appVersion),
  },
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'favicon.ico', 'apple-touch-icon-180x180.png'],
      manifest: {
        name: 'Monohull — automated dev environments for IBM Maximo',
        short_name: 'Monohull',
        description: 'Orchestrate Docker-based Maximo development environments from your phone.',
        theme_color: '#6366f1',
        background_color: '#0a0e1a',
        display: 'standalone',
        orientation: 'any',
        start_url: '/',
        scope: '/',
        icons: [
          { src: 'pwa-64x64.png', sizes: '64x64', type: 'image/png' },
          { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          { src: 'maskable-icon-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // App-shell precache (Vite-emitted JS/CSS/HTML). API and SSE streams must hit the
        // network — never cache them.
        //
        // Navigations are NetworkFirst rather than served from the precache: the
        // document's RESPONSE HEADERS (notably the CSP) ship with the cached copy, so
        // precache-first kept enforcing the previous release's CSP until the service
        // worker updated — which is how the container terminal stayed blocked after
        // the 1.0.2 upgrade. Online loads now always fetch the live shell; the cached
        // copy (per visited URL) is only an offline fallback.
        runtimeCaching: [
          {
            urlPattern: ({ url }) => url.pathname.startsWith('/api/'),
            handler: 'NetworkOnly',
          },
          {
            urlPattern: ({ request, url }) => request.mode === 'navigate' && !url.pathname.startsWith('/api/'),
            handler: 'NetworkFirst',
            options: {
              cacheName: 'app-shell',
              networkTimeoutSeconds: 4,
            },
          },
          {
            urlPattern: /^https:\/\/fonts\.(?:googleapis|gstatic)\.com\/.*/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'google-fonts',
              expiration: { maxEntries: 20, maxAgeSeconds: 60 * 60 * 24 * 365 },
            },
          },
        ],
      },
    }),
  ],
  server: {
    port: 3000,
    allowedHosts: true,
    proxy: {
      // ws: the container-terminal endpoint upgrades to a websocket. The backend only
      // accepts same-origin handshakes (the terminal is auth'd by session cookie), so
      // rewrite Origin to match the proxy target in dev.
      '/api': {
        target: 'http://localhost:8080',
        ws: true,
        headers: { Origin: 'http://localhost:8080' },
      }
    }
  },
  build: {
    outDir: '../java/src/main/resources/static',
    emptyOutDir: true
  }
})
