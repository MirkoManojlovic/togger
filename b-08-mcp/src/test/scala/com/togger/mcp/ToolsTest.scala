package com.togger.mcp

import chimp.protocol.ToolContent
import chimp.server.*
import com.togger.mcp.ToolSupport.McpTool
import com.togger.mcp.tools.*
import io.circe.parser.parse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import sttp.model.StatusCode

class ToolsTest extends AnyWordSpec with Matchers:
  private val config =
    McpConfig("http://backend:8081", "127.0.0.1", 8090, McpConfig.AccountEmail, McpConfig.AccountPassword, None)
  private val projectId = "22222222-2222-2222-2222-222222222222"
  private val otherId = "33333333-3333-3333-3333-333333333333"

  private def withBackend(body: String = "[]") =
    val stub = RecordingBackend {
      case SeenRequest("POST", uri, _, _) if uri.endsWith("/account/login") => (StatusCode.Ok, RecordingBackend.LoginOk)
      case _ => (StatusCode.Ok, RecordingBackend.envelope(body))
    }
    val client = TgBackendClient(config, stub.backend)
    val _ = client.login()
    stub.seen.clear()
    (stub, client)

  private def call(tool: McpTool, arguments: String): ToolResult[?] =
    val json = parse(arguments).toOption.get
    val run = tool.asInstanceOf[ServerTool[Any, Any, sttp.shared.Identity, ServerContext[sttp.shared.Identity]]]
    val input = run.inputDecoder.decodeJson(json).toOption.get
    run.logic(input, ServerContext.noop, Nil)

  private def text(result: ToolResult[?]): String =
    result.content.collect { case ToolContent.Text(_, text) => text }.mkString

  private def named(tools: List[McpTool], name: String): McpTool = tools.find(_.name == name).get

  "every tool" should {
    "have a unique name and be marked read-only" in {
      val (_, client) = withBackend()
      val all = OrganizationTools(client).all ++ ProjectTools(client).all ++ TaskTools(client).all ++
        MilestoneTools(client).all ++ AccountTools(client).all

      all.map(_.name).distinct should have size all.size
      all.size shouldBe 16
      all.foreach(_.annotations.flatMap(_.readOnlyHint) shouldBe Some(true))
    }
  }

  "list_tasks" should {
    "turn every filter into a query parameter" in {
      val (stub, client) = withBackend("""{"items":[]}""")
      val result = call(
        named(TaskTools(client).all, "list_tasks"),
        s"""{"projectId":"$projectId","statusIds":["$otherId"],"priorities":[1,2],"labelIds":["$otherId"],
           |"assigneeIds":["$otherId"],"milestoneIds":["$otherId"],"search":" bug ","timeboxId":"$otherId",
           |"includeArchived":true,"page":2,"size":50}""".stripMargin
      )

      result.isError shouldBe false
      text(result) shouldBe """{"items":[]}"""
      val uri = stub.seen.single.uri
      uri should startWith(s"http://backend:8081/task/project-id/$projectId?")
      uri should include(s"statusId=$otherId")
      uri should include("priority=1")
      uri should include("priority=2")
      uri should include(s"labelId=$otherId")
      uri should include(s"assigneeId=$otherId")
      uri should include(s"milestoneId=$otherId")
      uri should include("search=bug")
      uri should include(s"timeboxId=$otherId")
      uri should include("includeArchived=true")
      uri should include("page=2")
      uri should include("size=50")
      stub.seen.single.method shouldBe "GET"
    }

    "send no query when no filter is given" in {
      val (stub, client) = withBackend()
      val _ = call(named(TaskTools(client).all, "list_tasks"), s"""{"projectId":"$projectId"}""")
      stub.seen.single.uri shouldBe s"http://backend:8081/task/project-id/$projectId"
    }

    "reject ids that are not UUIDs without calling the backend" in {
      val (stub, client) = withBackend()
      val result = call(named(TaskTools(client).all, "list_tasks"), """{"projectId":"../../account"}""")
      result.isError shouldBe true
      text(result) should include("projectId must be a UUID")
      stub.seen shouldBe empty
    }
  }

  "get_project_overview" should {
    "combine the project, its task statuses and its task labels" in {
      val (stub, client) = withBackend("""{"x":1}""")
      val result = call(named(ProjectTools(client).all, "get_project_overview"), s"""{"projectId":"$projectId"}""")

      stub.seen.map(_.uri) shouldBe List(
        s"http://backend:8081/project/$projectId",
        s"http://backend:8081/task-status/project/$projectId?size=100",
        s"http://backend:8081/task-label/project/$projectId?size=100"
      )
      parse(text(result)).toOption.get.hcursor.keys.map(_.toSet) shouldBe Some(
        Set("project", "taskStatuses", "taskLabels")
      )
    }
  }

  "search_projects_by_labels" should {
    "post the label ids" in {
      val (stub, client) = withBackend()
      val _ =
        call(named(ProjectTools(client).all, "search_projects_by_labels"), s"""{"projectLabelIds":["$otherId"]}""")
      stub.seen.single.method shouldBe "POST"
      stub.seen.single.uri shouldBe "http://backend:8081/project/search"
      stub.seen.single.body shouldBe Some(s"""{"projectLabelIds":["$otherId"]}""")
    }
  }

  "other tools" should {
    "call the matching backend paths" in {
      val (stub, client) = withBackend()
      val organizations = OrganizationTools(client).all
      val _ = call(named(organizations, "list_organizations"), """{"page":1,"size":10}""")
      val _ = call(named(organizations, "get_organization"), s"""{"organizationId":"$otherId"}""")
      val _ = call(named(organizations, "list_organization_members"), s"""{"organizationId":"$otherId"}""")
      val _ = call(named(ProjectTools(client).all, "list_projects"), s"""{"organizationId":"$otherId"}""")
      val _ = call(named(ProjectTools(client).all, "list_project_members"), s"""{"projectId":"$projectId"}""")
      val _ = call(named(ProjectTools(client).all, "list_project_labels"), "{}")
      val _ = call(named(TaskTools(client).all, "get_task"), s"""{"taskId":"$otherId"}""")
      val _ = call(
        named(MilestoneTools(client).all, "list_milestones"),
        s"""{"projectId":"$projectId","statuses":[0,2],"search":"q1"}"""
      )
      val _ = call(named(MilestoneTools(client).all, "get_milestone"), s"""{"milestoneId":"$otherId"}""")
      val _ = call(named(AccountTools(client).all, "search_accounts"), """{"term":"ann"}""")

      stub.seen.map(_.uri) shouldBe List(
        "http://backend:8081/organization?page=1&size=10",
        s"http://backend:8081/organization/$otherId",
        s"http://backend:8081/organization/members/$otherId",
        s"http://backend:8081/project/organization/$otherId",
        s"http://backend:8081/project/members/$projectId",
        "http://backend:8081/project-label",
        s"http://backend:8081/task/$otherId",
        s"http://backend:8081/milestone/project/$projectId?status=0&status=2&search=q1",
        s"http://backend:8081/milestone/$otherId",
        "http://backend:8081/account/term/ann"
      )
    }

    "return the backend error as a tool error" in {
      val stub = RecordingBackend {
        case SeenRequest("POST", _, _, _) => (StatusCode.Ok, RecordingBackend.LoginOk)
        case _                            => (StatusCode.NotFound, "")
      }
      val client = TgBackendClient(config, stub.backend)
      val _ = client.login()
      val result = call(named(TaskTools(client).all, "get_task"), s"""{"taskId":"$otherId"}""")
      result.isError shouldBe true
      text(result) shouldBe "Not found (404)"
    }
  }

  extension [A](buffer: scala.collection.mutable.ListBuffer[A])
    private def single: A =
      buffer.toList match
        case List(only) => only
        case other      => fail(s"expected one request, got ${other.size}: $other")
