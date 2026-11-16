import './app-config.js'
import './style.css'
import './apexcharts-bridge.js'
import '@linkOutputDir/main.js'

import {open} from '@tauri-apps/plugin-shell';
import {invoke} from '@tauri-apps/api/core';
import {check} from '@tauri-apps/plugin-updater';
import {relaunch} from '@tauri-apps/plugin-process';
import {open as openDialog} from '@tauri-apps/plugin-dialog';

// the Scala.js UI picks the import/export directories through this; resolves to null when the dialog is cancelled.
// Registered in every Tauri window (also `tauri dev`, which runs the web frontend), never in a plain browser
if (window.__TAURI_INTERNALS__) {
  window.tgDirectoryPicker = {
    pick: async (current) => {
      const picked = await openDialog({directory: true, multiple: false, defaultPath: current || undefined})
      return typeof picked === "string" ? picked : null
    }
  }
}

// The Scala.js status page controls the backend and the MCP server through this. The Rust side (processes.rs) owns the
// processes, starts them at launch, checks them every minute and stops them when the app exits. `app` is "backend" or
// "mcp"; without it the call is for both. Every call answers the status of both apps.
if (window.__TAURI_INTERNALS__) {
  window.tgBackend = {
    status: () => invoke('backend_status'),
    start: (app) => invoke('backend_start', {app: app || null}),
    stop: (app) => invoke('backend_stop', {app: app || null}),
    restart: (app) => invoke('backend_restart', {app: app || null})
  }
}

if (appConfig.isDesktopMode === "true") {
  // a webview does not hand links to the browser of the system, so web links that ask for a new tab are opened there
  document.addEventListener('click', event => {
    const link = event.target instanceof Element ? event.target.closest('a[target="_blank"]') : null
    if (link && /^https?:\/\//i.test(link.href)) {
      event.preventDefault()
      open(link.href).catch(error => console.log('Error:', error))
    }
  })

  // the Scala.js UI drives updates through this; the sidecars are stopped first because they lock the app files
  let pendingUpdate = null
  window.tgUpdater = {
    check: async () => {
      pendingUpdate = await check()
      return pendingUpdate ? {version: pendingUpdate.version, notes: pendingUpdate.body || ""} : null
    },
    install: async () => {
      if (!pendingUpdate) throw new Error("No update has been checked")
      await invoke('backend_stop', {app: null})
      await pendingUpdate.downloadAndInstall()
      await relaunch()
    }
  }
}
