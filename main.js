import './app-config.js'
import './style.css'
import './apexcharts-bridge.js'
import '@linkOutputDir/main.js'

import {Command, open} from '@tauri-apps/plugin-shell';
import {resolveResource} from '@tauri-apps/api/path';
import {getCurrentWindow} from '@tauri-apps/api/window';
import {check} from '@tauri-apps/plugin-updater';
import {relaunch} from '@tauri-apps/plugin-process';

if (appConfig.isDesktopMode === "true") {
  async function load() {
    const resourcePath = await resolveResource('java/');
    const command = Command.sidecar('binaries/backend-app-exec', resourcePath);
    const output = await command.spawn();
  }

  async function loadMcp() {
    const resourcePath = await resolveResource('mcp/');
    return await Command.sidecar('binaries/mcp-app-exec', resourcePath).spawn();
  }

  // a webview does not hand links to the browser of the system, so web links that ask for a new tab are opened there
  document.addEventListener('click', event => {
    const link = event.target instanceof Element ? event.target.closest('a[target="_blank"]') : null
    if (link && /^https?:\/\//i.test(link.href)) {
      event.preventDefault()
      open(link.href).catch(error => console.log('Error:', error))
    }
  })

  async function stopSidecars() {
    await fetch("http://localhost:8081/stop-app")
    const mcp = await mcpChild
    if (mcp) await mcp.kill()
  }

  // the Scala.js UI drives updates through this; the sidecars are stopped first because they lock the app files
  let pendingUpdate = null
  window.tgUpdater = {
    check: async () => {
      pendingUpdate = await check()
      return pendingUpdate ? {version: pendingUpdate.version, notes: pendingUpdate.body || ""} : null
    },
    install: async () => {
      if (!pendingUpdate) throw new Error("No update has been checked")
      await stopSidecars()
      await pendingUpdate.downloadAndInstall()
      await relaunch()
    }
  }

  load().then(res => console.log("Loaded"))
  const mcpChild = loadMcp().catch(error => console.log('Error:', error))

  getCurrentWindow().onCloseRequested(async (event) => {
      try {
        const response = await fetch("http://localhost:8081/stop-app")
        const mcp = await mcpChild
        if (mcp) await mcp.kill()
        window.destroy()
      } catch (error) {
        console.log('Error:', error)
      }
    }
  ).then(_ => console.log("Closed"))
}
