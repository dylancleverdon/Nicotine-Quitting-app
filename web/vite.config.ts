import { defineConfig } from 'vite'
import preact from '@preact/preset-vite'
import { VitePWA } from 'vite-plugin-pwa'
import { readFileSync } from 'node:fs'

const pkg = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8'))

// Served from GitHub Pages at /Nicotine-Quitting-app/ (override with BASE for other hosts).
export default defineConfig({
  base: process.env.BASE ?? '/Nicotine-Quitting-app/',
  define: { __APP_VERSION__: JSON.stringify(pkg.version) },
  plugins: [
    preact(),
    VitePWA({
      // Updates install themselves: a new version activates on the next open.
      registerType: 'autoUpdate',
      includeAssets: ['icon.svg'],
      manifest: {
        name: 'Firewatch by Baastik Labs',
        short_name: 'Firewatch',
        description: 'Track nicotine in pieces and taper down.',
        theme_color: '#140E0C',
        background_color: '#140E0C',
        display: 'standalone',
        icons: [
          { src: 'icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any maskable' },
          { src: 'icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png' },
        ],
      },
      workbox: { globPatterns: ['**/*.{js,mjs,css,html,svg,png,md}'], maximumFileSizeToCacheInBytes: 8_000_000 },
    }),
  ],
  // The engine is linked from ../core; resolve its npm dependencies from this app.
  resolve: { dedupe: ['@js-joda/core', '@js-joda/timezone', 'format-util'] },
  optimizeDeps: { include: ['firewatch-core'] },
  server: { fs: { allow: ['..'] } },
})
