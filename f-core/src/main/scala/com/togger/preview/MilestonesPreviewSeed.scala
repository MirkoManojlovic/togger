package com.togger.preview

import com.togger.common.TgClock
import com.togger.dto.{FindMilestoneDateResp, FindMilestoneSummaryResp}
import com.togger.milestone.MilestoneStatus

import java.time.temporal.ChronoUnit
import java.time.{DayOfWeek, LocalDate, ZoneOffset}
import java.util.UUID

/** Seeds milestones around the clock's today, with their dates and summaries; milestone dates are always work days. */
class MilestonesPreviewSeed(data: MilestonesPreviewData, projects: ProjectsPreviewData, clock: TgClock)
    extends PreviewSeeder {
  import MilestonesPreviewSeed._

  private val today = clock.currentDate(ZoneOffset.UTC)

  override def seed(): Unit = {
    val now = clock.currentInstant
    specs.foreach { spec =>
      val workDays = projects.projects.find(spec.projectId).fold(Set(1, 2, 3, 4, 5))(_.workDays).map(DayOfWeek.of)
      def day(offset: Int): LocalDate = workDay(today.plusDays(offset), workDays)
      val start = day(spec.start)
      data.milestones.upsert(
        PreviewMilestone(
          id = spec.id,
          projectId = spec.projectId,
          createdAt = start.atStartOfDay().toInstant(ZoneOffset.UTC).minus(3, ChronoUnit.DAYS),
          latestVersion = PreviewSeedIds.id(PreviewSeedIds.Milestone, spec.number + 1000),
          name = spec.name,
          description = spec.description,
          statusValue = spec.status.value,
          startDate = start,
          dueDate = day(spec.due),
          endDate = spec.end.map(day),
          updatedAt = now.minus(1, ChronoUnit.DAYS)
        )
      )
      spec.dates.zipWithIndex.foreach { case ((offset, name, description), i) =>
        val at = day(offset).atTime(9, 0).toInstant(ZoneOffset.UTC)
        data.dates.upsert(
          FindMilestoneDateResp(
            PreviewSeedIds.id(PreviewSeedIds.MilestoneDate, spec.number * 10 + i),
            spec.id,
            at.minus(10, ChronoUnit.DAYS),
            PreviewSeedIds.id(PreviewSeedIds.MilestoneDate, spec.number * 10 + i + 1000),
            name,
            description,
            at,
            at.minus(2, ChronoUnit.DAYS)
          )
        )
      }
      spec.summaries.zipWithIndex.foreach { case ((from, to, text), i) =>
        val written = day(to).atTime(17, 0).toInstant(ZoneOffset.UTC)
        data.summaries.upsert(
          FindMilestoneSummaryResp(
            PreviewSeedIds.id(PreviewSeedIds.MilestoneSummary, spec.number * 10 + i),
            spec.id,
            written,
            PreviewSeedIds.id(PreviewSeedIds.MilestoneSummary, spec.number * 10 + i + 1000),
            text,
            day(from),
            day(to),
            written
          )
        )
      }
    }
  }
}

object MilestonesPreviewSeed {

  /** Offsets are in days from today; the dates are moved back to the previous work day of the project. */
  private case class MilestoneSpec(
    number: Int,
    projectId: UUID,
    name: String,
    description: String,
    status: MilestoneStatus,
    start: Int,
    due: Int,
    end: Option[Int],
    dates: List[(Int, String, String)],
    summaries: List[(Int, Int, String)]
  ) {
    def id: UUID = milestoneId(number)
  }

  def milestoneId(number: Int): UUID = PreviewSeedIds.id(PreviewSeedIds.Milestone, number)

  val FoundationsId: UUID = milestoneId(1)
  val BetaId: UUID = milestoneId(2)
  val GeneralAvailabilityId: UUID = milestoneId(3)
  val LegacySunsetId: UUID = milestoneId(4)
  val MobileMvpId: UUID = milestoneId(5)
  val MobileBetaId: UUID = milestoneId(6)
  val DocsRevampId: UUID = milestoneId(7)
  val DocsTutorialsId: UUID = milestoneId(8)

  private def workDay(date: LocalDate, workDays: Set[DayOfWeek]): LocalDate =
    Iterator.iterate(date)(_.minusDays(1)).find(d => workDays.contains(d.getDayOfWeek)).get

  private val specs: List[MilestoneSpec] = List(
    MilestoneSpec(
      1,
      PreviewDemo.ProjectId,
      "Platform foundations",
      "Authentication, the core data model and the CI pipeline.",
      MilestoneStatus.Done,
      -100,
      -45,
      Some(-43),
      List(
        (-90, "Data model frozen", "Schema reviewed and migrations agreed."),
        (-45, "Foundations done", "Everything merged and deployed to staging.")
      ),
      List((-100, -70, "## First half\n\n- Authentication and sessions shipped.\n- CI pipeline runs in under ten minutes.\n"))
    ),
    MilestoneSpec(
      2,
      PreviewDemo.ProjectId,
      "Beta release",
      "Invite-only beta with the dashboards and the task management features.",
      MilestoneStatus.InProgress,
      -42,
      12,
      None,
      List(
        (-30, "Feature freeze", "No new features after this date, only fixes."),
        (-5, "Internal dogfooding", "The whole team uses the beta for daily work."),
        (10, "Beta invites sent", "First customers get access.")
      ),
      List(
        (-42, -29, "## Weeks 1-2\n\n- Task statuses and labels landed.\n- Milestone dates are editable.\n"),
        (-28, -15, "## Weeks 3-4\n\n- Dashboards show contributions per week.\n- Two blocking bugs fixed in the export.\n")
      )
    ),
    MilestoneSpec(
      3,
      PreviewDemo.ProjectId,
      "General availability",
      "Public launch with billing, onboarding and the status page.",
      MilestoneStatus.InProgress,
      13,
      75,
      None,
      List((30, "Pricing decision", "Final pricing agreed with the business."), (70, "Launch day", "Public announcement.")),
      List.empty
    ),
    MilestoneSpec(
      4,
      PreviewDemo.ProjectId,
      "Legacy API sunset",
      "Retire the v1 API once all customers have moved to v2.",
      MilestoneStatus.Paused,
      -60,
      -10,
      None,
      List((-20, "Customer notice", "Customers still on v1 were notified.")),
      List.empty
    ),
    MilestoneSpec(
      5,
      PreviewDemo.SecondProjectId,
      "Mobile MVP",
      "Login, task list and quick log entry on iOS and Android.",
      MilestoneStatus.Done,
      -70,
      -28,
      Some(-30),
      List((-35, "App store review", "Submitted to both stores.")),
      List((-70, -30, "## MVP\n\n- Login and task list shipped.\n- Quick log entry works offline.\n"))
    ),
    MilestoneSpec(
      6,
      PreviewDemo.SecondProjectId,
      "Mobile beta",
      "Push notifications, widgets and the first round of beta feedback.",
      MilestoneStatus.InProgress,
      -27,
      20,
      None,
      List((-3, "TestFlight build", "Build 0.9 for the beta group."), (18, "Beta feedback closes", "Collected feedback is triaged.")),
      List((-27, -10, "## Beta so far\n\n- Push notifications work on both platforms.\n- Widget design approved.\n"))
    ),
    MilestoneSpec(
      7,
      OrganizationsPreviewSeed.SideProjectId,
      "Docs revamp",
      "New structure, search and a consistent writing style.",
      MilestoneStatus.InProgress,
      -21,
      9,
      None,
      List((-7, "Style guide published", "Contributors follow the guide from now on."), (8, "Old site redirects", "Redirects from the old URLs go live.")),
      List((-21, -8, "## Revamp\n\n- Navigation restructured.\n- Search indexes the whole site.\n"))
    ),
    MilestoneSpec(
      8,
      OrganizationsPreviewSeed.SideProjectId,
      "Beginner tutorials",
      "A series of hands-on tutorials for first time users.",
      MilestoneStatus.Abandoned,
      -50,
      -20,
      Some(-20),
      List.empty,
      List.empty
    )
  )
}
