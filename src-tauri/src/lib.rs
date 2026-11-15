mod processes;

use processes::ProcessManager;
use tauri::{Manager, RunEvent};

/// The sidecars only run in an installed app; `tauri dev` serves the web frontend and uses its own backend.
fn runs_sidecars() -> bool {
    !cfg!(debug_assertions) || std::env::var("TOGGER_SIDECARS").is_ok_and(|v| v == "1")
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .plugin(tauri_plugin_process::init())
        .plugin(tauri_plugin_dialog::init())
        .manage(ProcessManager::default())
        .invoke_handler(tauri::generate_handler![
            processes::backend_status,
            processes::backend_start,
            processes::backend_stop,
            processes::backend_restart
        ])
        .setup(|app| {
            if cfg!(debug_assertions) {
                app.handle().plugin(
                    tauri_plugin_log::Builder::default()
                        .level(log::LevelFilter::Info)
                        .build(),
                )?;
            }
            if runs_sidecars() {
                let handle = app.handle().clone();
                std::thread::spawn(move || {
                    let _ = handle.state::<ProcessManager>().start_all(&handle);
                });
                ProcessManager::start_monitor(app.handle().clone());
            }
            Ok(())
        })
        .build(tauri::generate_context!())
        .expect("error while building tauri application")
        .run(|app, event| {
            if let RunEvent::Exit = event {
                if runs_sidecars() {
                    let _ = app.state::<ProcessManager>().stop_all(true);
                }
            }
        });
}
