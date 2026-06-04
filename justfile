build-tauri-backend:
    sbt "backendMain/Universal/packageZipTarball"
    rm -rf b-07-main/target/universal/deploy
    mkdir -p b-07-main/target/universal/deploy
    cp b-07-main/target/universal/business-main-0.1.0-SNAPSHOT.tgz b-07-main/target/universal/deploy
    tar xfvz b-07-main/target/universal/deploy/business-main-0.1.0-SNAPSHOT.tgz -C b-07-main/target/universal/deploy
    rm -rf src-tauri/java/*
    cp -r b-07-main/target/universal/deploy/business-main-0.1.0-SNAPSHOT/* src-tauri/java

build-tauri-app:
    open src-tauri/target/release/bundle/macos
    npm run tauri build -- --verbose

clean:
    sbt clean

build-tauri-backend-clean: clean build-tauri-backend

desktop: build-tauri-backend build-tauri-app
desktop-clean: build-tauri-backend-clean build-tauri-app