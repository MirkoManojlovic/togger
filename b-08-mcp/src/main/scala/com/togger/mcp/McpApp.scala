package com.togger.mcp

import cats.effect.{Deferred, IO}
import cats.effect.unsafe.implicits.global
import chimp.server.McpServer
import com.togger.mcp.tools.*
import sttp.client4.DefaultSyncBackend
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.sync.NettySyncServer

object McpApp extends TgLogging:
  private val DesktopMode = "desktop"
  private val LoginAttempts = 30
  private val LoginRetryDelayMillis = 2000L

  /** `McpApp desktop` stops the server when the Togger desktop app is no longer running, like the backend does. */
  def main(args: Array[String]): Unit =
    val config = McpConfig.fromEnv()
    val client = TgBackendClient(config, DefaultSyncBackend())

    loginWithRetry(client, config) match
      case Left(error) =>
        logger.error(s"Cannot log in to the Togger backend: $error")
        sys.exit(1)
      case Right(_) => logger.info(s"Logged in to ${config.backendUrl} as ${config.email}")

    val tools =
      OrganizationTools(client).all ++ ProjectTools(client).all ++ TaskTools(client).all ++
        MilestoneTools(client).all ++ MilestoneStepTools(client).all ++ MilestoneTemplateTools(client).all ++
        AccountTools(client).all

    val mcpServer = McpServer(tools = tools)
      .name("togger-mcp")
      .version(Option(getClass.getPackage.getImplementationVersion).getOrElse("dev"))
      .instructions(
        "Read-only access to Togger: organizations, projects, members, labels, task statuses, tasks, milestones with their steps, and milestone templates. " +
          "Start with list_organizations, then list_projects, then get_project_overview. All ids are UUIDs."
      )

    if args.headOption.contains(DesktopMode) then
      logger.info("Desktop mode: stopping when the Togger desktop app is no longer running")
      val condition = CheckDesktopAppRunningCondition()
      val watch =
        for
          stopSignal <- Deferred[IO, Unit]
          _ <- CheckDesktopAppRunning().run(() => condition.isRunning, stopSignal)
          _ <- IO(sys.exit(0))
        yield ()
      watch.unsafeRunAndForget()

    logger.info(s"Togger MCP listening on http://${config.host}:${config.port}/mcp with ${tools.size} tools")
    NettySyncServer()
      .host(config.host)
      .port(config.port)
      .addEndpoint(protect(mcpServer.endpoint(List("mcp")), config))
      .startAndWait()

  // The mcp account is an admin, so when TOGGER_MCP_API_KEY is set every MCP call must carry it as a bearer token.
  private def protect(endpoint: ServerEndpoint[Any, Identity], config: McpConfig): ServerEndpoint[Any, Identity] =
    config.apiKey match
      case None => endpoint
      case Some(apiKey) =>
        endpoint.prependSecurityPure(auth.bearer[String](), statusCode(StatusCode.Unauthorized))(token =>
          Either.cond(token == apiKey, (), ())
        )

  private def loginWithRetry(client: TgBackendClient, config: McpConfig): Either[String, Unit] =
    LazyList
      .from(1)
      .take(LoginAttempts)
      .map { attempt =>
        val result = client.login()
        result.left.foreach { error =>
          logger.warn(s"Login attempt $attempt/$LoginAttempts to ${config.backendUrl} failed: $error")
          if attempt < LoginAttempts then Thread.sleep(LoginRetryDelayMillis)
        }
        result
      }
      .find(_.isRight)
      .getOrElse(Left(s"gave up after $LoginAttempts attempts"))
