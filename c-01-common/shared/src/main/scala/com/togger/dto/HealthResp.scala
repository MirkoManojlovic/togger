package com.togger.dto

/** Answer of the public health check of the backend (`app` = "backend") and of the MCP server (`app` = "mcp"). */
case class HealthResp(app: String, version: String)
