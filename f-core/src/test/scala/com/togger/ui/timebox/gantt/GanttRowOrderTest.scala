package com.togger.ui.timebox.gantt

import com.togger.dto.{FindTaskRespFull, FindTaskStatusResp, SearchAccountDto}
import com.togger.task.{DefaultTaskStatus, TaskPriority}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Instant, LocalDate}
import java.util.UUID

class GanttRowOrderTest extends AnyWordSpec with Matchers {

  private val projectId = UUID.randomUUID()
  private val member = SearchAccountDto(UUID.randomUUID(), "Ada", "Lovelace", "ada@example.com", UUID.randomUUID())
  private val backlog = FindTaskStatusResp(UUID.randomUUID(), projectId, DefaultTaskStatus.Backlog.statusName, "")

  private def task(name: String, assignee: Option[SearchAccountDto] = None) = FindTaskRespFull(
    id = UUID.randomUUID(),
    projectId = projectId,
    name = name,
    description = "",
    assignedTo = assignee,
    createdBy = member,
    closedBy = None,
    taskStatus = backlog,
    priority = TaskPriority.High,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
    latestVersion = UUID.randomUUID(),
    logEntries = Seq.empty,
    links = Seq.empty,
    startDate = LocalDate.of(2026, 10, 12),
    dueDate = LocalDate.of(2026, 10, 16)
  )

  private val (ta, tb, tc) = (task("A"), task("B"), task("C"))

  "GanttRowOrder.ordered" should {
    "keep the staging order without an arranged order" in {
      GanttRowOrder.ordered(Seq(ta, tb, tc), Seq.empty) shouldBe Seq(ta, tb, tc)
    }

    "follow the arranged order" in {
      GanttRowOrder.ordered(Seq(ta, tb, tc), Seq(tc.id, ta.id, tb.id)) shouldBe Seq(tc, ta, tb)
    }

    "put newly staged tasks last" in {
      GanttRowOrder.ordered(Seq(ta, tb, tc), Seq(tb.id, ta.id)) shouldBe Seq(tb, ta, tc)
    }

    "drop tasks that are no longer staged" in {
      GanttRowOrder.ordered(Seq(ta, tc), Seq(tb.id, tc.id, ta.id)) shouldBe Seq(tc, ta)
    }

    "group new tasks by assignee in the order of the members, the unassigned last" in {
      val (ada, bob) = (member, member.copy(id = UUID.randomUUID()))
      val members = Seq(ada.id, bob.id).sortBy(_.toString)
      val (first, second) = (if (members.head == ada.id) (ada, bob) else (bob, ada))
      val (unassigned, ofSecond1, ofFirst, ofSecond2) =
        (task("U"), task("S1", Some(second)), task("F", Some(first)), task("S2", Some(second)))
      GanttRowOrder.ordered(Seq(unassigned, ofSecond1, ofFirst, ofSecond2), Seq.empty, members) shouldBe
        Seq(ofFirst, ofSecond1, ofSecond2, unassigned)
    }

    "put the arranged tasks before the grouped ones" in {
      val ada = member
      val (unassigned, assigned) = (task("U"), task("A", Some(ada)))
      GanttRowOrder.ordered(Seq(assigned, unassigned), Seq(unassigned.id), Seq(ada.id)) shouldBe
        Seq(unassigned, assigned)
    }
  }
}
