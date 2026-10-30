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
