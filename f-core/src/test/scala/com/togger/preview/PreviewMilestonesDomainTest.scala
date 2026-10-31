package com.togger.preview

import com.togger.demo.DemoMilestonesData
import com.togger.demo.DemoAccountsData

import com.togger.demo.DemoIdentity

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.common.{BadRequest, Forbidden, PageRequest}
import com.togger.dto._
import com.togger.milestone.MilestoneStatus
import com.togger.ui.milestone._

import java.time.{Instant, LocalDate}
import java.util.UUID
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

class PreviewMilestonesDomainTest extends AsyncJsWordSpec {

  private val firstPage = PageRequest(1, 50)

  private class Services(val env: PreviewTestEnv = new PreviewTestEnv) {
    private val m = env.module
    val milestones = new MilestoneFeServicePreview(m.context, m.projects, m.milestones)
    val dates = new MilestoneDateFeServicePreview(m.context, m.milestones)
    val aiClient = new MilestoneAiSummaryStreamClientPreview(m.context, m.projects, m.milestones, m.taskStats, 1.milli)
    val summaries = new MilestoneSummaryFeServicePreview(m.context, m.projects, m.milestones, aiClient)
    env.loginAsDemo()
  }

  private def isBadRequest(result: Either[_, _]): Boolean = result.left.exists(_.isInstanceOf[BadRequest])

  testNameFromClassName should {

    "list the seeded milestones by due date around the clock's today" in { _ =>
      val s = new Services
      val today = LocalDate.parse("2026-06-15")
      s.env.run(s.milestones.findByProjectId(DemoIdentity.ProjectId, MilestoneFilter.empty, firstPage)).map { result =>
        val items = result.toOption.get.items
        items.map(_.name) shouldBe List("Platform foundations", "Legacy API sunset", "Beta release", "General availability")
        items.map(_.dueDate).sorted shouldBe items.map(_.dueDate)
        items.find(_.name == "Beta release").map(m => m.startDate.isBefore(today) && m.dueDate.isAfter(today)) shouldBe Some(true)
        items.forall(m => (1 to 5).contains(m.startDate.getDayOfWeek.getValue)) shouldBe true
      }
    }

    "filter by status and search" in { _ =>
      val s = new Services
      for {
        done <- s.env.run(
          s.milestones.findByProjectId(DemoIdentity.ProjectId, MilestoneFilter(statuses = List(MilestoneStatus.Done)), firstPage)
        )
        searched <- s.env.run(
          s.milestones.findByProjectId(DemoIdentity.ProjectId, MilestoneFilter(search = Some(" BETA ")), firstPage)
        )
        paged <- s.env.run(s.milestones.findByProjectId(DemoIdentity.ProjectId, MilestoneFilter.empty, PageRequest(2, 3)))
      } yield {
        done.map(_.items.map(_.name)) shouldBe Right(List("Platform foundations"))
        searched.map(_.items.map(_.name)) shouldBe Right(List("Beta release"))
        paged.map(r => (r.items.size, r.totalNumberOfItems)) shouldBe Right((1, 4L))
      }
    }

    "create, update with a new version and validate the work days" in { _ =>
      val s = new Services
      val monday = LocalDate.parse("2026-07-06")
      for {
        weekend <- s.env.run(
          s.milestones.createMilestone(CreateMilestoneReq(DemoIdentity.ProjectId, "Weekend", "", LocalDate.parse("2026-07-04"), monday))
        )
        created <- s.env.run(s.milestones.createMilestone(CreateMilestoneReq(DemoIdentity.ProjectId, "Hardening", "d", monday, monday.plusDays(4))))
        id = created.toOption.get.id
        updated <- s.env.run(
          s.milestones.updateMilestone(UpdateMilestoneReq(id, "Hardening 2", "d", Some(MilestoneStatus.Paused), dueDate = Some(monday.plusDays(11))))
        )
        beforeStart <- s.env.run(s.milestones.updateMilestone(UpdateMilestoneReq(id, "x", "", dueDate = Some(monday.minusDays(3)))))
        found <- s.env.run(s.milestones.findMilestoneById(id))
        all <- s.env.run(s.milestones.findAllByProjectId(DemoIdentity.ProjectId))
        missing <- s.env.run(s.milestones.findMilestoneById(UUID.randomUUID()))
      } yield {
        isBadRequest(weekend) shouldBe true
        updated.map(m => (m.name, m.status, m.dueDate)) shouldBe Right(("Hardening 2", MilestoneStatus.Paused, monday.plusDays(11)))
        updated.map(_.latestVersion != created.toOption.get.latestVersion) shouldBe Right(true)
        isBadRequest(beforeStart) shouldBe true
        found.map(_.name) shouldBe Right("Hardening 2")
        all.map(_.size) shouldBe Right(5)
        isBadRequest(missing) shouldBe true
      }
    }

    "validate and list milestone dates by milestone and by project" in { _ =>
      val s = new Services
      val beta = DemoMilestonesData.BetaId
      for {
        byMilestone <- s.env.run(s.dates.findAllByMilestoneId(beta))
        byProject <- s.env.run(s.dates.findAllByProjectId(DemoIdentity.ProjectId))
        tooEarly <- s.env.run(s.dates.createMilestoneDate(CreateMilestoneDateReq(beta, "Early", "", Instant.parse("2020-01-01T09:00:00Z"))))
        created <- s.env.run(s.dates.createMilestoneDate(CreateMilestoneDateReq(beta, "Demo", "", Instant.parse("2026-06-20T09:00:00Z"))))
        updated <- s.env.run(
          s.dates.updateMilestoneDate(UpdateMilestoneDateReq(created.toOption.get.id, "Demo day", "", Instant.parse("2026-06-22T09:00:00Z")))
        )
      } yield {
        byMilestone.map(_.map(_.name)) shouldBe Right(List("Feature freeze", "Internal dogfooding", "Beta invites sent"))
        byProject.map(_.size) shouldBe Right(8)
        isBadRequest(tooEarly) shouldBe true
        updated.map(_.name) shouldBe Right("Demo day")
      }
    }

    "keep summaries behind project membership and valid periods" in { _ =>
      val s = new Services
      val beta = DemoMilestonesData.BetaId
      def request(from: String, to: String) = CreateMilestoneSummaryReq(beta, "text", LocalDate.parse(from), LocalDate.parse(to))
      for {
        seeded <- s.env.run(s.summaries.findAllByMilestoneId(beta))
        backwards <- s.env.run(s.summaries.createMilestoneSummary(request("2026-06-10", "2026-06-01")))
        created <- s.env.run(s.summaries.createMilestoneSummary(request("2026-06-01", "2026-06-12")))
        updated <- s.env.run(
          s.summaries.updateMilestoneSummary(UpdateMilestoneSummaryReq(created.toOption.get.id, "new text", LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-14")))
        )
        deleted <- s.env.runEmpty(s.summaries.deleteMilestoneSummary(created.toOption.get.id))
        _ = s.env.loginAs(DemoAccountsData.FifthAccountId)
        outsider <- s.env.run(s.summaries.findByMilestoneId(DemoMilestonesData.MobileBetaId, firstPage))
      } yield {
        seeded.map(_.size) shouldBe Right(2)
        isBadRequest(backwards) shouldBe true
        updated.map(_.summary) shouldBe Right("new text")
        deleted.isRight shouldBe true
        outsider shouldBe Left(Forbidden)
      }
    }

    "stream a fake AI summary in chunks and then finish" in { _ =>
      val s = new Services
      val events = ListBuffer.empty[AiSummaryEvent]
      val req = SummariseMilestoneWithAiReq(
        DemoMilestonesData.BetaId,
        None,
        "Summarise",
        LocalDate.parse("2026-06-01"),
        LocalDate.parse("2026-06-12"),
        "gpt-4.1"
      )
      s.env.runIO(s.summaries.summariseWithAi(req)(events += _)).map { _ =>
        events.last shouldBe AiSummaryEvent.Done
        val text = events.collect { case AiSummaryEvent.Chunk(t) => t }.mkString
        (events.size > 5) shouldBe true
        text should include("Beta release")
      }
    }

    "report an AI failure for a backwards period, missing login and stranger" in { _ =>
      val s = new Services
      def run(req: SummariseMilestoneWithAiReq): scala.concurrent.Future[List[AiSummaryEvent]] = {
        val events = ListBuffer.empty[AiSummaryEvent]
        s.env.runIO(s.summaries.summariseWithAi(req)(events += _)).map(_ => events.toList)
      }
      val backwards = SummariseMilestoneWithAiReq(DemoMilestonesData.BetaId, None, "p", LocalDate.parse("2026-06-12"), LocalDate.parse("2026-06-01"), "m")
      val foreign = backwards.copy(milestoneId = DemoMilestonesData.MobileBetaId, startDate = LocalDate.parse("2026-06-01"), endDate = LocalDate.parse("2026-06-12"))
      for {
        wrongDates <- run(backwards)
        _ = s.env.loginAs(DemoAccountsData.FifthAccountId)
        stranger <- run(foreign)
        _ = s.env.loginRepo.delete()
        loggedOut <- run(backwards)
      } yield {
        wrongDates.map(_.getClass) shouldBe List(classOf[AiSummaryEvent.Failed])
        stranger shouldBe List(AiSummaryEvent.Failed("You are not a member of this project"))
        loggedOut shouldBe List(AiSummaryEvent.Failed("Please log in again"))
      }
    }

    "use the tasks activity in the AI summary and drop milestones with their project" in { _ =>
      val s = new Services
      s.env.module.taskStats.source = new TaskStatsSource {
        override def statusInUse(statusId: UUID): Boolean = false
        override def activityOf(milestoneId: UUID, from: LocalDate, to: LocalDate) =
          List(MilestoneActivity("Fix login", "Done", List("Found the cause in the session cache")))
      }
      val events = ListBuffer.empty[AiSummaryEvent]
      val req = SummariseMilestoneWithAiReq(DemoMilestonesData.BetaId, None, "p", LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-12"), "m")
      for {
        _ <- s.env.runIO(s.summaries.summariseWithAi(req)(events += _))
        _ = s.env.module.projects.deleteProject(DemoIdentity.ProjectId)
      } yield {
        events.collect { case AiSummaryEvent.Chunk(t) => t }.mkString should include("**Fix login** (Done): Found the cause")
        s.env.module.milestones.milestones.filter(_.projectId == DemoIdentity.ProjectId) shouldBe Nil
        s.env.module.milestones.dates.all.exists(_.milestoneId == DemoMilestonesData.BetaId) shouldBe false
        s.env.module.milestones.summaries.all.exists(_.milestoneId == DemoMilestonesData.BetaId) shouldBe false
      }
    }
  }
}
