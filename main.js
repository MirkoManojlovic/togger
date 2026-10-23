import './app-config.js'
import './style.css'
import './apexcharts-bridge.js'
import '@linkOutputDir/main.js'

import {Command, open} from '@tauri-apps/plugin-shell';
import {resolveResource} from '@tauri-apps/api/path';
import {getCurrentWindow} from '@tauri-apps/api/window';

if (appConfig.isDesktopMode === "true") {
  async function load() {
    const resourcePath = await resolveResource('java/');
    const command = Command.sidecar('binaries/backend-app-exec', resourcePath);
    const output = await command.spawn();
  }

  // a webview does not hand links to the browser of the system, so web links that ask for a new tab are opened there
  document.addEventListener('click', event => {
    const link = event.target instanceof Element ? event.target.closest('a[target="_blank"]') : null
    if (link && /^https?:\/\//i.test(link.href)) {
      event.preventDefault()
      open(link.href).catch(error => console.log('Error:', error))
    }
  })

  load().then(res => console.log("Loaded"))

  getCurrentWindow().onCloseRequested(async (event) => {
      try {
        const response = await fetch("http://localhost:8081/stop-app")
        window.destroy()
      } catch (error) {
        console.log('Error:', error)
      }
    }
  ).then(_ => console.log("Closed"))
}
