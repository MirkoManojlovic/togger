package com.togger.mcp

import java.util.UUID

import chimp.server.*
import io.circe.Json
import sttp.shared.Identity

object ToolSupport:
  type McpTool = ServerTool[?, ?, Identity, ServerContext[Identity]]

  private val ReadOnly =
    ToolAnnotations(readOnlyHint = Some(true), destructiveHint = Some(false), idempotentHint = Some(true))

  def readOnlyTool(name: String, description: String): PartialTool =
    tool(name).description(description).withAnnotations(ReadOnly)

  def respond(result: Either[String, Json]): ToolResult[NoStructuredOutput] =
    result.fold(ToolResult.error, json => ToolResult.text(json.deepDropNullValues.noSpaces))

  def uuid(field: String, value: String): Either[String, String] =
    try Right(UUID.fromString(value.trim).toString)
    catch case _: IllegalArgumentException => Left(s"$field must be a UUID, got '$value'")

  def uuids(field: String, values: Option[List[String]]): Either[String, List[String]] =
    values.getOrElse(Nil).foldRight[Either[String, List[String]]](Right(Nil)) { (value, acc) =>
      for
        valid <- uuid(field, value)
        rest <- acc
      yield valid :: rest
    }

  def paging(page: Option[Int], size: Option[Int]): List[(String, String)] =
    page.map("page" -> _.toString).toList ++ size.map("size" -> _.toString).toList

  def repeated(param: String, values: List[String]): List[(String, String)] =
    values.map(param -> _)
