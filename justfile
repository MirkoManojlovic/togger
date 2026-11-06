update-emoji-data:
    node tools/emoji/build-emoji-data.mjs

# sbt 2 writes build output under target/out/<platform>/<scala version>/<project>/
# db_mode (development -> togger-local-development, prod -> togger-local) is baked into the bundle as java/db-mode
build-tauri-backend db_mode:
    #!/usr/bin/env bash
    set -euo pipefail
    sbt "backendMain/Universal/packageZipTarball"
    universal_dir=$(echo target/out/jvm/*/backend-main/universal)
    rm -rf "$universal_dir/deploy"
    mkdir -p "$universal_dir/deploy"
    tgz=$(ls -t "$universal_dir"/backend-main-*.tgz | head -1)
    tar xfvz "$tgz" -C "$universal_dir/deploy"
    mkdir -p src-tauri/java
    rm -rf src-tauri/java/*
    cp -r "$universal_dir/deploy/$(basename "$tgz" .tgz)/"* src-tauri/java
    mkdir src-tauri/java/liquibase
    cp b-05-database/src/main/resources/liquibase/changelog.yaml src-tauri/java/liquibase/
    echo -n "{{db_mode}}" > src-tauri/java/db-mode

build-tauri-mcp:
    #!/usr/bin/env bash
    set -euo pipefail
    sbt "backendMcp/Universal/packageZipTarball"
    universal_dir=$(echo target/out/jvm/*/backend-mcp/universal)
    rm -rf "$universal_dir/deploy"
    mkdir -p "$universal_dir/deploy"
    tgz=$(ls -t "$universal_dir"/backend-mcp-*.tgz | head -1)
    tar xfvz "$tgz" -C "$universal_dir/deploy"
    mkdir -p src-tauri/mcp
    rm -rf src-tauri/mcp/*
    cp -r "$universal_dir/deploy/$(basename "$tgz" .tgz)/"* src-tauri/mcp

build-tauri-app:
    CI=true PATH="$PATH:/Library/Developer/CommandLineTools/usr/bin" npm run tauri build -- --verbose
    open src-tauri/target/release/bundle/macos

clean:
    sbt clean

build-tauri-backend-clean db_mode: clean (build-tauri-backend db_mode)

desktop-dev: (build-tauri-backend "development") build-tauri-mcp build-tauri-app
desktop-prod: (build-tauri-backend "prod") build-tauri-mcp build-tauri-app
desktop-dev-clean: (build-tauri-backend-clean "development") build-tauri-mcp build-tauri-app
desktop-prod-clean: (build-tauri-backend-clean "prod") build-tauri-mcp build-tauri-app

# Local development. The frontend needs `sbt ~frontendMain/fastLinkJS` running next to it (`frontend-watch`).
changelog_path := "b-05-database/src/main/resources/"

# runs `npm i` only when node_modules is missing or older than package-lock.json
npm-install:
    #!/usr/bin/env bash
    set -euo pipefail
    if [ ! -d node_modules ] || [ package-lock.json -nt node_modules ]; then
        npm i
        touch node_modules
    fi

# Links the ScalaJS frontend once (against the running sbt server), so vite serves the current code
frontend-link:
    sbt --client "frontendMain/fastLinkJS"

# Continuous frontend compilation (`~frontendMain/fastLinkJS`) in an sbt JVM of its own (no sbt server), so the
# backend `runMain` on the shared sbt server cannot block it. Runs in the foreground.
frontend-watch:
    sbt -Dsbt.client=false -Dsbt.server.autostart=false "~frontendMain/fastLinkJS"

# Starts `frontend-watch` in the background (log: target/frontend-watch.log, pid: target/frontend-watch.pid)
[private]
frontend-watch-start:
    #!/usr/bin/env bash
    set -uo pipefail
    mkdir -p target
    if [ -f target/frontend-watch.pid ] && kill -0 "$(cat target/frontend-watch.pid)" 2>/dev/null; then
        echo "frontend watch already running"
        exit 0
    fi
    nohup just frontend-watch > target/frontend-watch.log 2>&1 &
    echo $! > target/frontend-watch.pid
    echo "frontend watch started (log: target/frontend-watch.log)"

# Stops the `frontend-watch` started by this checkout (found by its pid file, so a watcher of another checkout survives).
stop-frontend-watch:
    #!/usr/bin/env bash
    set -uo pipefail
    kill_tree() {
        for child in $(pgrep -P "$1" || true); do kill_tree "$child"; done
        kill "$1" 2>/dev/null || true
    }
    if [ -f target/frontend-watch.pid ]; then
        kill_tree "$(cat target/frontend-watch.pid)"
        rm -f target/frontend-watch.pid
    fi
    echo "frontend watch stopped"

# Links the frontend, keeps compiling it in the background and runs vite; the watch stops with vite
frontend-web: npm-install frontend-link frontend-watch-start
    #!/usr/bin/env bash
    set -uo pipefail
    trap 'just stop-frontend-watch' EXIT
    npm run dev

frontend-tauri-dev: npm-install
    npm run tauri dev

# debug build of the Tauri app (not the optimized release bundle)
frontend-tauri-debug: npm-install
    npm run tauri build -- --debug

backend-web:
    sbt --client "backendMain/runMain com.togger.BackendApp web {{changelog_path}}"

# db_mode: development -> togger-local-development, prod -> togger-local
backend-desktop db_mode="development":
    sbt --client "backendMain/runMain com.togger.BackendApp desktop {{changelog_path}} {{db_mode}}"

# Fills a migrated development database with the demo data; refuses to run twice (stops when the demo admin exists)
backend-demo-seed:
    sbt --client "backendMain/runMain com.togger.maintenance.DemoSeedApp"

# Seeds the demo data (skipped with a message when it fails, e.g. because it is already there), then starts the backend
backend-web-demo:
    -just backend-demo-seed
    just backend-web

# Links the frontend, keeps compiling it (frontend-watch), runs vite in the background and the backend, without seeding
web: npm-install frontend-link frontend-watch-start
    #!/usr/bin/env bash
    set -uo pipefail
    npm run dev &
    frontend_pid=$!
    trap 'kill "$frontend_pid" 2>/dev/null || true; just stop-frontend-watch' EXIT
    just backend-web

# Links the frontend, keeps compiling it (frontend-watch), seeds the demo data, runs vite in the background and the backend
web-demo: npm-install frontend-link frontend-watch-start
    #!/usr/bin/env bash
    set -uo pipefail
    just backend-demo-seed || echo "Demo seed did not run (already seeded?), starting the backend anyway"
    npm run dev &
    frontend_pid=$!
    trap 'kill "$frontend_pid" 2>/dev/null || true; just stop-frontend-watch' EXIT
    just backend-web

# Stops the vite dev server (port 5173), `tauri dev` and the frontend watch of this checkout. Never touches the sbt server.
stop-frontend: stop-frontend-watch
    #!/usr/bin/env bash
    set -uo pipefail
    pids=$(lsof -nP -tiTCP:5173 -sTCP:LISTEN || true)
    [ -z "$pids" ] || kill $pids
    pkill -f "tauri dev" || true
    echo "frontend stopped"

# Stops the backend (the forked JVM listening on port 8081), not the sbt server that started it.
stop-backend:
    #!/usr/bin/env bash
    set -uo pipefail
    pids=$(lsof -nP -tiTCP:8081 -sTCP:LISTEN || true)
    [ -z "$pids" ] || kill $pids
    echo "backend stopped"

# Starts the read-only MCP server on http://127.0.0.1:8090/mcp; it needs the backend running (it logs in as the mcp account)
mcp-start:
    sbt --client "backendMcp/runMain com.togger.mcp.McpApp"

# Stops the MCP server (the forked JVM, found by its main class as it listens on port 8090 only after its login to the
# backend succeeded), not the sbt server that started it.
mcp-stop:
    #!/usr/bin/env bash
    set -uo pipefail
    pids=$( (lsof -nP -tiTCP:8090 -sTCP:LISTEN; pgrep -f "bin/java .*com.togger.mcp.McpApp") 2>/dev/null | sort -u || true)
    [ -z "$pids" ] || kill $pids
    echo "mcp stopped"

stop-all: stop-frontend stop-backend mcp-stop

# Bumps the desktop app version in tauri.conf.json, Cargo.toml and Cargo.lock and commits it (no tag).
# Part of the release-togger skill: run it on a release/<version> branch, merge the PR, then run `just release-tag`.
release version:
    #!/usr/bin/env bash
    set -euo pipefail
    [[ "{{version}}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "version must look like 1.2.3"; exit 1; }
    [[ -z "$(git status --porcelain)" ]] || { echo "working tree is not clean"; exit 1; }
    ! git rev-parse "v{{version}}" >/dev/null 2>&1 || { echo "tag v{{version}} already exists"; exit 1; }
    sed -i.bak -E 's/^(  "version": ")[^"]+(",)$/\1{{version}}\2/' src-tauri/tauri.conf.json
    sed -i.bak -E '0,/^version = "[^"]+"$/s//version = "{{version}}"/' src-tauri/Cargo.toml
    rm src-tauri/*.bak
    (cd src-tauri && cargo update --workspace --offline)
    git add src-tauri/tauri.conf.json src-tauri/Cargo.toml src-tauri/Cargo.lock
    git commit -m "chore(project): release {{version}}"
    echo "Committed the release {{version}}. Push the branch, open the PR and merge it, then run: just release-tag {{version}}"

# Tags v<version> on develop once the release PR is merged, and prints the push command that starts the release.
release-tag version:
    #!/usr/bin/env bash
    set -euo pipefail
    [[ "$(git branch --show-current)" == "develop" ]] || { echo "not on develop"; exit 1; }
    [[ -z "$(git status --porcelain)" ]] || { echo "working tree is not clean"; exit 1; }
    git fetch origin
    [[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/develop)" ]] || { echo "develop differs from origin/develop"; exit 1; }
    app_version=$(node -p "require('./src-tauri/tauri.conf.json').version")
    [[ "$app_version" == "{{version}}" ]] || { echo "tauri.conf.json is at $app_version, not {{version}}"; exit 1; }
    ! git rev-parse "v{{version}}" >/dev/null 2>&1 || { echo "tag v{{version}} already exists"; exit 1; }
    git tag "v{{version}}"
    echo "Tagged v{{version}}. Push with: git push origin v{{version}}"

# fetch, fast-forward develop, delete local branches/worktrees whose remote branch is gone, rebase <branch> (default: current branch) on develop
sync-branch branch='':
    #!/usr/bin/env bash
    set -euo pipefail
    base="develop"
    branch="{{branch}}"
    if [[ -z "$branch" ]]; then
        branch=$(git branch --show-current)
    fi
    if [[ -z "$branch" || "$branch" == "$base" ]]; then
        echo "Pass the branch to rebase: the current checkout is '${branch:-detached HEAD}'." >&2
        exit 1
    fi

    git fetch --all --prune

    # 'git fetch origin develop:develop' is refused while develop is checked out, so fast-forward it in place then.
    base_worktree=$(git worktree list --porcelain | awk -v ref="refs/heads/$base" '/^worktree /{wt=substr($0,10)} $0=="branch "ref{print wt}')
    if [[ -n "$base_worktree" ]]; then
        git -C "$base_worktree" merge --ff-only "origin/$base"
    else
        git fetch origin "$base:$base"
    fi

    # Local branches whose upstream no longer exists on the remote.
    gone_branches=$(git for-each-ref --format='%(refname:short) %(upstream:track)' refs/heads | awk '$2=="[gone]"{print $1}')
    for gone in $gone_branches; do
        if [[ "$gone" == "$base" ]]; then
            continue
        fi
        worktree=$(git worktree list --porcelain | awk -v ref="refs/heads/$gone" '/^worktree /{wt=substr($0,10)} $0=="branch "ref{print wt}')
        if [[ -n "$worktree" ]]; then
            echo "Removing worktree $worktree ($gone)"
            git worktree remove "$worktree"
        fi
        echo "Deleting branch $gone"
        git branch -D "$gone"
    done
    git worktree prune

    if ! git show-ref --verify --quiet "refs/heads/$branch"; then
        echo "Branch '$branch' does not exist locally (or was deleted because its remote branch is gone)." >&2
        exit 1
    fi

    # Rebase in the worktree where the branch is checked out, or check it out here otherwise.
    branch_worktree=$(git worktree list --porcelain | awk -v ref="refs/heads/$branch" '/^worktree /{wt=substr($0,10)} $0=="branch "ref{print wt}')
    if [[ -n "$branch_worktree" ]]; then
        git -C "$branch_worktree" rebase "$base"
    else
        git checkout "$branch"
        git rebase "$base"
    fi
