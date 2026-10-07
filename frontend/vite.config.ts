import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': new URL('./src', import.meta.url).pathname,
    },
  },
  server: {
    // Proxying keeps the browser on a single origin in development, so the
    // session cookie is first-party and CORS never enters the picture.
    //
    // changeOrigin rewrites the Host header to the backend, which makes Spring
    // build the OAuth redirect_uri as http://localhost:8080/... - the exact URI
    // registered in Google Cloud.
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/oauth2': { target: 'http://localhost:8080', changeOrigin: true },
      '/login': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
