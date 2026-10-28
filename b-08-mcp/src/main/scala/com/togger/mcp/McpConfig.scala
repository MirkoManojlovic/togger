package com.togger.mcp

case class McpConfig(
  backendUrl: String,
  host: String,
  port: Int,
  email: String,
  password: String,
  apiKey: Option[String]
)

object McpConfig:
  // Must match McpAccountSeedService in backendMain, which creates this account on every backend start.
  val AccountEmail: String = "mcp@email.com"
  val AccountPassword: String = "A!good_1_password_is_good"

  private val DefaultBackendUrl = "http://localhost:8081"
  private val DefaultHost = "localhost"
  private val DefaultPort = 8090

  def fromEnv(env: Map[String, String] = sys.env): McpConfig =
    McpConfig(
      backendUrl = env.getOrElse("TOGGER_BACKEND_URL", DefaultBackendUrl).stripSuffix("/"),
      host = env.getOrElse("TOGGER_MCP_HOST", DefaultHost),
      port = env.get("TOGGER_MCP_PORT").flatMap(_.toIntOption).getOrElse(DefaultPort),
      email = AccountEmail,
      password = AccountPassword,
      apiKey = env.get("TOGGER_MCP_API_KEY").filter(_.nonEmpty)
    )
