import { defineConfig } from "vite";
import { spawnSync } from "child_process";
import { resolve } from 'path';
import tailwindcss from '@tailwindcss/vite';

function isDev() {
  return process.env.NODE_ENV !== "production";
}

function printSbtTask(task) {
  const args = ["--error", "--batch", `print ${task}`];
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

  return result.stdout.toString("utf8").trim();
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

export default defineConfig({
  server: {
    watch: {
      ignored: ["**/.devenv/**"],
      followSymlinks: false,
    },
  },
   build:  {
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
  plugins: [
  multiPageAppIndexRouting(),
  tailwindcss()
  ]
});
