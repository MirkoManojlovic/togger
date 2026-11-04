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

# Local development. The frontend needs `sbt ~frontendMain/fastLinkJS` running next to it.
changelog_path := "b-05-database/src/main/resources/"

# runs `npm i` only when node_modules is missing or older than package-lock.json
npm-install:
    #!/usr/bin/env bash
    set -euo pipefail
    if [ ! -d node_modules ] || [ package-lock.json -nt node_modules ]; then
        npm i
        touch node_modules
    fi

frontend-web: npm-install
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

# Frontend (npm run dev) in the background, stopped with the backend; the demo seed runs before the backend starts
web-demo: npm-install
    #!/usr/bin/env bash
    set -uo pipefail
    just backend-demo-seed || echo "Demo seed did not run (already seeded?), starting the backend anyway"
    npm run dev &
    frontend_pid=$!
    trap 'kill "$frontend_pid" 2>/dev/null || true' EXIT
    just backend-web

# Stops the vite dev server (port 5173) and `tauri dev`. Never touches sbt.
stop-frontend:
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

stop-all: stop-frontend stop-backend

# Bumps the desktop app version in tauri.conf.json, Cargo.toml and Cargo.lock, commits and tags v<version>.
# The sbt version follows the tag (sbt-dynver). Push with `git push origin develop v<version>` to start the release.
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
    git tag "v{{version}}"
    echo "Tagged v{{version}}. Push with: git push origin develop v{{version}}"
