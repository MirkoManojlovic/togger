// throwaway change to test the preview cleanup
import { defineConfig } from "vite";
import { spawnSync } from "child_process";
import { existsSync } from "fs";
import { isAbsolute, resolve } from 'path';
import tailwindcss from '@tailwindcss/vite';

const host = process.env.TAURI_DEV_HOST;


function isDev() {
  return process.env.NODE_ENV !== "production";
}

function printSbtTask(task) {
  // sbt 2 runs through a thin client that prints status lines (and nothing at all against a warm server), so
  // the client is disabled and the output directory is picked out of whatever sbt prints
  const args = ["-Dsbt.client=false", "--error", "--batch", `print ${task}`];
  const options = {
    stdio: [
      "pipe", // StdIn.
      "pipe", // StdOut.
      "inherit", // StdErr.
    ],
  };

  const result =
    process.platform == "win32"
      ? spawnSync(
        "sbt.bat",
        args.map((x) => `"${x}"`),
        { shell: true, ...options }
      )
      : spawnSync("sbt", args, options);

  if (result.error) throw result.error;
  if (result.status !== 0)
    throw new Error(`sbt process failed with exit code ${result.status}`);

  const outputDir = result.stdout
    .toString("utf8")
    .split("\n")
    .map((line) => line.replace(/\x1b\[[0-9;]*[A-Za-z]/g, "").trim())
    .find((line) => isAbsolute(line) && existsSync(line));
  if (!outputDir) throw new Error(`sbt did not print an existing directory for ${task}`);

  return outputDir;
}

const linkOutputDir = printSbtTask(
  isDev() ? "fastLinkOutputDir" : "fullLinkOutputDir"
);



// Middleware
const multiPageAppIndexRouting = () => ({
  name: "configure-server",
  configureServer(server) {
    return () => {
      server.middlewares.use(async (req, res, next) => {

        // Route to home dev app :)
        if (req.originalUrl.startsWith("")) {
          req.url = "/index.html";
        }

        for (const appName in server.config.build.rollupOptions.input) {
          if (req.originalUrl.startsWith(`/${appName}`)) {
            req.url = `/${appName}/index.html`;
            break;
          }
        }

        next();
      });
    };
  }
});

function gitOutput(args) {
  const result = spawnSync("git", args, { encoding: "utf8" });
  return result.status === 0 ? result.stdout.trim() : "";
}

// the commit hash, plus "-dirty" while there are uncommitted changes, so two different builds never share storage
function previewBuildId() {
  // CI builds a merge commit of the pull request, so it passes the commit that is actually being previewed
  if (process.env.PREVIEW_BUILD_ID) return process.env.PREVIEW_BUILD_ID;
  const hash = gitOutput(["rev-parse", "--short", "HEAD"]) || "unknown";
  return gitOutput(["status", "--porcelain"]) ? `${hash}-dirty` : hash;
}

const previewBuild = previewBuildId();

export default defineConfig(({ mode }) => {
  const isPreview = mode === "preview";
  return {
  // prevent vite from obscuring rust errors
  clearScreen: false,
  server: {
    // make sure this port matches the devUrl port in tauri.conf.json file
    port: 5173,
    // Tauri expects a fixed port, fail if that port is not available
    strictPort: true,
    // if the host Tauri is expecting is set, use it
    host: host || false,
    hmr: host
        ? {
          protocol: 'ws',
          host,
          port: 1421,
        }
        : undefined,
    watch: {
      ignored: ["**/.devenv/**", '**/src-tauri/**'],
      followSymlinks: false,
    },
  },
  // Env variables starting with the item of `envPrefix` will be exposed in tauri's source code through `import.meta.env`.
  envPrefix: ['VITE_', 'TAURI_ENV_*'],
   build:  {
     // Tauri uses Chromium on Windows and WebKit on macOS and Linux
     target:
       process.env.TAURI_ENV_PLATFORM == 'windows'
         ? 'chrome105'
         : 'safari13',
     // don't minify for debug builds
     minify: !process.env.TAURI_ENV_DEBUG ? 'esbuild' : false,
     // produce sourcemaps for debug builds
     sourcemap: !!process.env.TAURI_ENV_DEBUG,
     rollupOptions: {
       input: {
         app: resolve(__dirname, "index.html"),
       },
     },
   },
  resolve: {
    alias: [
      { find: "@linkOutputDir", replacement: linkOutputDir },
    ],
  },
  appType: 'mpa',
  define: {
    "import.meta.env.VITE_PREVIEW_BUILD_ID": JSON.stringify(previewBuild),
  },
  plugins: [
  multiPageAppIndexRouting(),
  tailwindcss(),
  {
    // index.html reads the theme before the app starts, so it needs the same key prefix as the app
    name: "tg-storage-prefix",
    transformIndexHtml: (html) => html.replace("__TG_STORAGE_PREFIX__", isPreview ? `${previewBuild}:` : ""),
  }
  ]
  };
});
