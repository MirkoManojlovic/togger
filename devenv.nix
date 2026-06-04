{
  pkgs,
  lib,
  ...
}: {
  # Disable warning for .env files (maybe include this later)
  dotenv.disableHint = true;

  languages.scala.enable = true;
  languages.scala.package = pkgs.scala;

  services.postgres = {
    enable = true;
    package = pkgs.postgresql_13;
    settings.listen_addresses = lib.mkForce "localhost";
  };

  scripts.kill-pg.exec = ''
    ps aux | grep -m1 /bin/postgres | awk '{print $2}' | xargs kill -9
  '';

  scripts.reset-pg.exec = ''
    cd liquibase &&
    ./liquibase --defaults-file=liquibase-local.properties dropAll &&
    ./liquibase --defaults-file=liquibase-local.properties update
  '';

  # https://devenv.sh/basics/
  env.GREET = "Togger";
  env.DOCKER_HOST = "unix://$HOME/.colima/default/docker.sock";

  # https://devenv.sh/packages/
  packages = [
    pkgs.nodejs
    pkgs.sbt
    pkgs.flyctl
    pkgs.colima
    pkgs.kubectl
    pkgs.docker
    pkgs.docker-compose
    pkgs.hugo
    pkgs.graalvmPackages.graalvm-ce
  ];

  # https://devenv.sh/scripts/
  scripts.hello.exec = "echo 🚀 Hello from $GREET 🚀";

  enterShell = ''
    hello
  '';

  processes = {
    # qcfe.exec = "npm run dev";
  };

  # https://devenv.sh/languages/
  # languages.nix.enable = true;
  languages.python.enable = true;

  languages.rust.enable = true;
  # https://devenv.sh/pre-commit-hooks/
  # pre-commit.hooks.shellcheck.enable = true;

  # https://devenv.sh/processes/
  # processes.ping.exec = "ping example.com";

  # See full reference at https://devenv.sh/reference/options/
}
