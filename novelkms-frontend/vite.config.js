import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { execSync } from 'child_process'
import { readFileSync } from 'fs'

function getBuildNumber() {
	try {
		return execSync('git rev-list --count HEAD', { encoding: 'utf-8' }).trim()
	} catch {
		return '0'
	}
}

function getAppVersion() {
	try {
		const pom = readFileSync('../pom.xml', 'utf-8')
		const match = pom.match(/<version>([^<]+)<\/version>/)
		return match ? match[1] : '0.0'
	} catch {
		return '0.0'
	}
}

export default defineConfig({
	plugins: [react()],
	define: {
		__BUILD_NUMBER__: JSON.stringify(getBuildNumber()),
		__APP_VERSION__: JSON.stringify(getAppVersion()),
	},
	optimizeDeps: {
		entries: ['src/main.jsx'],
	},
	server: {
		port: 3000,
		proxy: {
			'/api': {
				target: 'http://localhost:8080',
				changeOrigin: true,
			}
		}
	}
})