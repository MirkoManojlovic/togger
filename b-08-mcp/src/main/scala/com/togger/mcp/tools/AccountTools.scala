package com.togger.mcp.tools

import chimp.server.*
import com.togger.mcp.TgBackendClient
import com.togger.mcp.ToolSupport.*
import io.circe.Codec
import sttp.tapir.Schema

case class SearchAccountsInput(term: String, page: Option[Int], size: Option[Int]) derives Codec, Schema

class AccountTools(client: TgBackendClient):

  private val searchAccounts = readOnlyTool(
    "search_accounts",
    "Finds accounts whose first name, last name or email contains the term (case-insensitive). Use it to get the account UUIDs for list_tasks assigneeIds. Paged: page starts at 1, size is 1..100, default 20."
  ).input[SearchAccountsInput]
    .handle(input =>
      respond(
        if input.term.trim.isEmpty then Left("term must not be empty")
        else client.get(List("account", "term", input.term.trim), paging(input.page, input.size))
      )
    )

  def all: List[McpTool] = List(searchAccounts)
