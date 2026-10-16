import './app-config.js'
import './style.css'
import './apexcharts-bridge.js'
import '@linkOutputDir/main.js'

import {Command} from '@tauri-apps/plugin-shell';
import {resolveResource} from '@tauri-apps/api/path';
import {getCurrentWindow} from '@tauri-apps/api/window';

if (appConfig.isDesktopMode === "true") {
  async function load() {
    const resourcePath = await resolveResource('java/');
    const command = Command.sidecar('binaries/backend-app-exec', resourcePath);
    const output = await command.spawn();
  }

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
