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
    cp "$universal_dir/backend-main-0.1.0-SNAPSHOT.tgz" "$universal_dir/deploy"
    tar xfvz "$universal_dir/deploy/backend-main-0.1.0-SNAPSHOT.tgz" -C "$universal_dir/deploy"
    mkdir -p src-tauri/java
    rm -rf src-tauri/java/*
    cp -r "$universal_dir/deploy/backend-main-0.1.0-SNAPSHOT/"* src-tauri/java
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
    cp "$universal_dir/backend-mcp-0.1.0-SNAPSHOT.tgz" "$universal_dir/deploy"
    tar xfvz "$universal_dir/deploy/backend-mcp-0.1.0-SNAPSHOT.tgz" -C "$universal_dir/deploy"
    mkdir -p src-tauri/mcp
    rm -rf src-tauri/mcp/*
    cp -r "$universal_dir/deploy/backend-mcp-0.1.0-SNAPSHOT/"* src-tauri/mcp

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
