//! Starts, stops and watches the two sidecars of the desktop app: the backend (port 8081) and the MCP server (port 8090).
//!
//! The processes are controlled from here (not from the webview) so the control also works while the status page is
//! closed, and so a process left over from an older version of the app can still be found by its port and replaced.

use std::collections::HashMap;
use std::io::{Read, Write};
use std::net::{SocketAddr, TcpStream};
use std::process::Command;
use std::sync::Mutex;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};
use tauri::path::BaseDirectory;
use tauri::{AppHandle, Manager};
use tauri_plugin_shell::process::CommandChild;
use tauri_plugin_shell::ShellExt;

/// Version of this desktop app; the sidecars it starts must report the same one.
pub const APP_VERSION: &str = env!("CARGO_PKG_VERSION");

const CHECK_INTERVAL: Duration = Duration::from_secs(60);
const HEALTH_TIMEOUT: Duration = Duration::from_secs(2);
/// The backend closes its connections before it exits (up to 30 s), so a stop waits that long before it kills.
const STOP_TIMEOUT: Duration = Duration::from_secs(35);
const KILL_TIMEOUT: Duration = Duration::from_secs(5);
/// The backend needs a while to boot (database setup); within this time after a spawn it is "starting", not "down".
const STARTUP_GRACE: Duration = Duration::from_secs(120);
/// After this many failed restarts in a row the monitor only tries every `SLOW_RETRY_EVERY` checks.
const MAX_QUICK_RETRIES: u32 = 3;
const SLOW_RETRY_EVERY: u32 = 10;

#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum App {
    Backend,
    Mcp,
}

impl App {
    pub const ALL: [App; 2] = [App::Backend, App::Mcp];

    fn port(self) -> u16 {
        match self {
            App::Backend => 8081,
            App::Mcp => 8090,
        }
    }

    fn sidecar(self) -> &'static str {
        match self {
            App::Backend => "backend-app-exec",
            App::Mcp => "mcp-app-exec",
        }
    }

    fn resource_dir(self) -> &'static str {
        match self {
            App::Backend => "java/",
            App::Mcp => "mcp/",
        }
    }

    /// Only a process whose command line has one of these is killed when it is found by its port.
    fn process_markers(self) -> &'static [&'static str] {
        match self {
            App::Backend => &["com.togger.BackendApp", "backend-app", "backend-main"],
            App::Mcp => &["com.togger.mcp.McpApp", "backend-mcp"],
        }
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Health {
    pub app: String,
    pub version: String,
}

/// Parses the answer of `GET /health` of the backend (`{"body": {"app", "version"}}`) and of the MCP server (same shape).
pub fn parse_health(response: &str) -> Option<Health> {
    let (head, body) = response.split_once("\r\n\r\n")?;
    if !head.lines().next()?.contains(" 200") {
        return None;
    }
    let json: serde_json::Value = serde_json::from_str(body.trim()).ok()?;
    let payload = json.get("body").unwrap_or(&json);
    Some(Health {
        app: payload.get("app")?.as_str()?.to_string(),
        version: payload.get("version")?.as_str()?.to_string(),
    })
}

/// `0.4.0+3-abcd` and `0.4.0` are the same release: only the leading `major.minor.patch` part is compared.
pub fn same_release(a: &str, b: &str) -> bool {
    fn base(version: &str) -> &str {
        let end = version
            .find(|c: char| !(c.is_ascii_digit() || c == '.'))
            .unwrap_or(version.len());
        &version[..end]
    }
    let base_a = base(a.trim_start_matches('v'));
    !base_a.is_empty() && base_a == base(b.trim_start_matches('v'))
}

#[derive(Debug, PartialEq, Eq)]
pub enum Action {
    Nothing,
    Start,
    Restart,
}

/// What the monitor does for one app. `running` is the version it answers with, `None` when it does not answer.
pub fn is_starting(spawned_at: Option<Instant>, now: Instant) -> bool {
    spawned_at.is_some_and(|at| now.saturating_duration_since(at) < STARTUP_GRACE)
}

pub fn decide(running: Option<&str>, expected: &str, stopped_by_user: bool, failures_in_a_row: u32) -> Action {
    if stopped_by_user {
        return Action::Nothing;
    }
    match running {
        Some(version) if same_release(version, expected) => Action::Nothing,
        _ if failures_in_a_row >= MAX_QUICK_RETRIES && failures_in_a_row % SLOW_RETRY_EVERY != 0 => Action::Nothing,
        Some(_) => Action::Restart,
        None => Action::Start,
    }
}

fn now_secs() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0)
}

fn check_health(app: App) -> Option<Health> {
    let address = SocketAddr::from(([127, 0, 0, 1], app.port()));
    let mut stream = TcpStream::connect_timeout(&address, HEALTH_TIMEOUT).ok()?;
    stream.set_read_timeout(Some(HEALTH_TIMEOUT)).ok()?;
    stream.set_write_timeout(Some(HEALTH_TIMEOUT)).ok()?;
    stream
        .write_all(b"GET /health HTTP/1.0\r\nHost: localhost\r\nConnection: close\r\n\r\n")
        .ok()?;
    let mut response = String::new();
    stream.read_to_string(&mut response).ok()?;
    parse_health(&response)
}

fn port_is_open(app: App) -> bool {
    let address = SocketAddr::from(([127, 0, 0, 1], app.port()));
    TcpStream::connect_timeout(&address, Duration::from_millis(300)).is_ok()
}

fn listening_pids(app: App) -> Vec<String> {
    Command::new("lsof")
        .args(["-ti", &format!("tcp:{}", app.port()), "-sTCP:LISTEN"])
        .output()
        .map(|out| String::from_utf8_lossy(&out.stdout).lines().map(|l| l.trim().to_string()).collect())
        .unwrap_or_default()
}

fn is_togger_process(app: App, pid: &str) -> bool {
    Command::new("ps")
        .args(["-p", pid, "-o", "command="])
        .output()
        .map(|out| {
            let command = String::from_utf8_lossy(&out.stdout);
            app.process_markers().iter().any(|marker| command.contains(marker))
        })
        .unwrap_or(false)
}

fn signal(pid: &str, name: &str) {
    let _ = Command::new("kill").arg(name).arg(pid).output();
}

fn is_alive(pid: &str) -> bool {
    Command::new("kill")
        .args(["-0", pid])
        .output()
        .map(|out| out.status.success())
        .unwrap_or(false)
}

/// The port closes as soon as the server starts to shut down, but the process lives on until it has closed its
/// connections and the database pool. So the stop waits for the process itself, not for the port.
fn wait_until_gone(app: App, pids: &[String], timeout: Duration) -> bool {
    let deadline = Instant::now() + timeout;
    while (pids.iter().any(|pid| is_alive(pid)) || port_is_open(app)) && Instant::now() < deadline {
        std::thread::sleep(Duration::from_millis(200));
    }
    !pids.iter().any(|pid| is_alive(pid)) && !port_is_open(app)
}

/// Kills what listens on the port of the app, but only when it is a Togger process.
fn kill_listeners(app: App) -> Result<(), String> {
    let pids: Vec<String> = listening_pids(app).into_iter().filter(|pid| is_togger_process(app, pid)).collect();
    pids.iter().for_each(|pid| signal(pid, "-TERM"));
    if wait_until_gone(app, &pids, STOP_TIMEOUT) {
        return Ok(());
    }
    pids.iter().for_each(|pid| signal(pid, "-KILL"));
    if wait_until_gone(app, &pids, KILL_TIMEOUT) {
        return Ok(());
    }
    Err(format!("{} is still running after the stop (port {})", app.sidecar(), app.port()))
}

#[derive(Default)]
struct AppState {
    child: Option<CommandChild>,
    spawned_at: Option<Instant>,
    stopped_by_user: bool,
    failures_in_a_row: u32,
    restarts: u32,
    last_error: Option<String>,
    last_check: Option<u64>,
}

#[derive(Serialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct AppStatus {
    pub app: App,
    pub running: bool,
    pub version: Option<String>,
    /// Process id of what listens on the port of the app.
    pub pid: Option<u32>,
    pub expected_version: String,
    pub version_mismatch: bool,
    pub stopped_by_user: bool,
    pub restarts: u32,
    pub last_error: Option<String>,
    pub last_check: Option<u64>,
}

#[derive(Default)]
pub struct ProcessManager {
    states: Mutex<HashMap<App, AppState>>,
}

impl ProcessManager {
    fn with_state<T>(&self, app: App, f: impl FnOnce(&mut AppState) -> T) -> T {
        let mut states = self.states.lock().unwrap_or_else(|e| e.into_inner());
        f(states.entry(app).or_default())
    }

    pub fn start(&self, handle: &AppHandle, app: App) -> Result<(), String> {
        self.with_state(app, |s| s.stopped_by_user = false);
        if check_health(app).is_some() || self.is_starting(app) {
            return Ok(());
        }
        let result = self.spawn(handle, app);
        self.with_state(app, |s| s.last_error = result.clone().err());
        result
    }

    /// Spawned a moment ago and not answering yet: a second spawn would only fail on the port.
    fn is_starting(&self, app: App) -> bool {
        self.with_state(app, |s| s.child.is_some() && is_starting(s.spawned_at, Instant::now()))
    }

    fn spawn(&self, handle: &AppHandle, app: App) -> Result<(), String> {
        let dir = handle
            .path()
            .resolve(app.resource_dir(), BaseDirectory::Resource)
            .map_err(|e| format!("cannot find {}: {e}", app.resource_dir()))?;
        let (mut rx, child) = handle
            .shell()
            .sidecar(app.sidecar())
            .map_err(|e| e.to_string())?
            .args([dir.to_string_lossy().to_string()])
            .spawn()
            .map_err(|e| e.to_string())?;
        // the output goes to the log files of the wrapper scripts; the channel only has to be drained
        tauri::async_runtime::spawn(async move { while rx.recv().await.is_some() {} });
        self.with_state(app, |s| {
            s.child = Some(child);
            s.spawned_at = Some(Instant::now());
        });
        Ok(())
    }

    pub fn stop(&self, app: App, by_user: bool) -> Result<(), String> {
        let child = self.with_state(app, |s| {
            s.stopped_by_user = by_user;
            s.spawned_at = None;
            s.child.take()
        });
        let listeners = kill_listeners(app);
        if let Some(child) = child {
            let _ = child.kill();
        }
        let result = listeners;
        self.with_state(app, |s| s.last_error = result.clone().err());
        result
    }

    pub fn restart(&self, handle: &AppHandle, app: App) -> Result<(), String> {
        self.stop(app, false)?;
        self.start(handle, app)
    }

    /// The MCP server logs in to the backend, so it stops first and starts last.
    pub fn restart_all(&self, handle: &AppHandle) -> Result<(), String> {
        self.stop(App::Mcp, false)?;
        self.stop(App::Backend, false)?;
        self.start(handle, App::Backend)?;
        self.start(handle, App::Mcp)
    }

    pub fn start_all(&self, handle: &AppHandle) -> Result<(), String> {
        self.start(handle, App::Backend)?;
        self.start(handle, App::Mcp)
    }

    pub fn stop_all(&self, by_user: bool) -> Result<(), String> {
        let mcp = self.stop(App::Mcp, by_user);
        mcp.and(self.stop(App::Backend, by_user))
    }

    pub fn status(&self) -> Vec<AppStatus> {
        App::ALL.iter().map(|app| self.status_of(*app)).collect()
    }

    fn status_of(&self, app: App) -> AppStatus {
        let health = check_health(app);
        let version = health.map(|h| h.version);
        let pid = listening_pids(app).first().and_then(|pid| pid.parse().ok());
        let mismatch = version.as_deref().is_some_and(|v| !same_release(v, APP_VERSION));
        self.with_state(app, |s| AppStatus {
            app,
            running: version.is_some(),
            version,
            pid,
            expected_version: APP_VERSION.to_string(),
            version_mismatch: mismatch,
            stopped_by_user: s.stopped_by_user,
            restarts: s.restarts,
            last_error: s.last_error.clone(),
            last_check: s.last_check,
        })
    }

    /// One round of the monitor: restarts what is down or runs another version than this app.
    pub fn check_and_heal(&self, handle: &AppHandle) {
        for app in App::ALL {
            let running = check_health(app).map(|h| h.version);
            let (stopped, failures) = self.with_state(app, |s| {
                s.last_check = Some(now_secs());
                (s.stopped_by_user, s.failures_in_a_row)
            });
            if running.is_none() && self.is_starting(app) {
                continue;
            }
            let action = decide(running.as_deref(), APP_VERSION, stopped, failures);
            let result = match action {
                Action::Nothing => Ok(()),
                Action::Start => self.start(handle, app),
                Action::Restart => self.restart(handle, app),
            };
            self.with_state(app, |s| {
                match (&action, &result) {
                    (Action::Nothing, _) if running.is_some() && !stopped => s.failures_in_a_row = 0,
                    (Action::Nothing, _) if stopped => (),
                    (Action::Nothing, _) => s.failures_in_a_row += 1,
                    (_, Ok(())) => s.restarts += 1,
                    (_, Err(_)) => s.failures_in_a_row += 1,
                }
                if let Err(error) = &result {
                    s.last_error = Some(error.clone());
                }
            });
        }
    }

    /// Checks every minute, in its own thread; a first check after the interval leaves the startup alone.
    pub fn start_monitor(handle: AppHandle) {
        std::thread::spawn(move || loop {
            std::thread::sleep(CHECK_INTERVAL);
            handle.state::<ProcessManager>().check_and_heal(&handle);
        });
    }
}

/// The calls block (health checks, waiting for a port), so they run on a thread of their own.
async fn blocking<T: Send + 'static>(
    handle: AppHandle,
    f: impl FnOnce(&ProcessManager, &AppHandle) -> T + Send + 'static,
) -> Result<T, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let manager = handle.state::<ProcessManager>();
        f(&manager, &handle)
    })
    .await
    .map_err(|e| e.to_string())
}

/// The commands take one app, or both when `app` is left out.
#[tauri::command]
pub async fn backend_status(handle: AppHandle) -> Result<Vec<AppStatus>, String> {
    blocking(handle, |manager, _| manager.status()).await
}

#[tauri::command]
pub async fn backend_start(handle: AppHandle, app: Option<App>) -> Result<Vec<AppStatus>, String> {
    blocking(handle, move |manager, handle| {
        match app {
            Some(app) => manager.start(handle, app),
            None => manager.start_all(handle),
        }
        .map(|_| manager.status())
    })
    .await?
}

#[tauri::command]
pub async fn backend_stop(handle: AppHandle, app: Option<App>) -> Result<Vec<AppStatus>, String> {
    blocking(handle, move |manager, _| {
        match app {
            Some(app) => manager.stop(app, true),
            None => manager.stop_all(true),
        }
        .map(|_| manager.status())
    })
    .await?
}

#[tauri::command]
pub async fn backend_restart(handle: AppHandle, app: Option<App>) -> Result<Vec<AppStatus>, String> {
    blocking(handle, move |manager, handle| {
        match app {
            Some(app) => manager.restart(handle, app),
            None => manager.restart_all(handle),
        }
        .map(|_| manager.status())
    })
    .await?
}

#[cfg(test)]
mod tests {
    use super::*;

    const OK: &str = "HTTP/1.1 200 OK\r\ncontent-type: application/json\r\n\r\n";

    #[test]
    fn parses_the_health_answer() {
        let response = format!("{OK}{{\"body\":{{\"app\":\"mcp\",\"version\":\"0.4.0\"}}}}");
        assert_eq!(parse_health(&response), Some(Health { app: "mcp".into(), version: "0.4.0".into() }));
    }

    #[test]
    fn ignores_an_error_answer_and_garbage() {
        assert_eq!(parse_health("HTTP/1.1 500 Internal Server Error\r\n\r\n{}"), None);
        assert_eq!(parse_health(&format!("{OK}not json")), None);
        assert_eq!(parse_health(""), None);
    }

    #[test]
    fn compares_only_the_release_part_of_the_version() {
        assert!(same_release("0.4.0", "0.4.0"));
        assert!(same_release("0.4.0+3-abcd", "0.4.0"));
        assert!(same_release("v0.4.0", "0.4.0"));
        assert!(!same_release("0.3.9", "0.4.0"));
        assert!(!same_release("", ""));
        assert!(!same_release("dev", "dev"));
    }

    #[test]
    fn leaves_a_healthy_app_alone() {
        assert_eq!(decide(Some("0.4.0"), "0.4.0", false, 0), Action::Nothing);
    }

    #[test]
    fn starts_an_app_that_is_down() {
        assert_eq!(decide(None, "0.4.0", false, 0), Action::Start);
    }

    #[test]
    fn restarts_an_app_with_another_version() {
        assert_eq!(decide(Some("0.3.0"), "0.4.0", false, 0), Action::Restart);
    }

    #[test]
    fn does_not_undo_a_stop_by_the_user() {
        assert_eq!(decide(None, "0.4.0", true, 0), Action::Nothing);
        assert_eq!(decide(Some("0.3.0"), "0.4.0", true, 0), Action::Nothing);
    }

    #[test]
    fn is_starting_only_within_the_grace_period() {
        let now = Instant::now();
        assert!(!is_starting(None, now));
        assert!(is_starting(Some(now), now));
        assert!(!is_starting(Some(now), now + STARTUP_GRACE));
    }

    #[test]
    fn backs_off_after_repeated_failures() {
        assert_eq!(decide(None, "0.4.0", false, 2), Action::Start);
        assert_eq!(decide(None, "0.4.0", false, 3), Action::Nothing);
        assert_eq!(decide(None, "0.4.0", false, 9), Action::Nothing);
        assert_eq!(decide(None, "0.4.0", false, 10), Action::Start);
    }
}
