package com.togger.tasktemplate

import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.TestLoggingJvm
import com.togger.common.TimeHelpers
import com.togger.db.MockedTransactor
import com.togger.suites.TestNameHelper
import com.togger.task.{TaskLabel, TaskLabelRepo}
import com.togger.tasktemplate.TaskTemplateError._
import doobie.ConnectionIO
import doobie.util.invariant.UnexpectedEnd
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID

class TaskTemplateServiceTest
    extends AsyncWordSpec
    with AsyncMockFactory
    with Matchers
    with TestNameHelper
    with MockedTransactor
    with TimeHelpers
    with TestLoggingJvm {

  private val templateRepo = mock[TaskTemplateRepo]
  private val versionRepo = mock[TaskTemplateVersionRepo]
  private val labelRepo = mock[TaskLabelRepo]

  private val service = new TaskTemplateService(templateRepo, versionRepo, labelRepo, transactorMock)

  private val projectId = UUID.randomUUID()
  private val accountId = UUID.randomUUID()
  private val labelId = UUID.randomUUID()

  private def template(labelId: Option[UUID] = None) =
    TaskTemplate(UUID.randomUUID(), projectId, labelId, accountId, nowAsInstant)

  private def version(templateId: UUID, name: String = "name", body: String = "body") =
    TaskTemplateVersion(UUID.randomUUID(), templateId, name, body, nowAsInstant)

  private def label(projectId: UUID = projectId) =
    TaskLabel(labelId, projectId, "feature", "", nowAsInstant, nowAsInstant)

  testNameFromClassName should {

    "create the template and its first version" in {
      val created = template()
      val first = version(created.id)
      val full = TaskTemplateFull(created, first, None)

      (templateRepo.insert _).expects(created).returns(created.pure[ConnectionIO])
      (versionRepo.insert _).expects(first).returns(first.pure[ConnectionIO])
      (templateRepo.findFullById _).expects(created.id).returns(full.pure[ConnectionIO])

      service.create(created, first).map(_ shouldBe Right(full)).unsafeToFuture()
    }

    "create a template linked to a free label of the project" in {
      val created = template(Some(labelId))
      val first = version(created.id)
      val full = TaskTemplateFull(created, first, Some("feature"))

      (labelRepo.findById _).expects(labelId).returns(label().pure[ConnectionIO])
      (templateRepo.findByLabelId _).expects(labelId).returns(none[TaskTemplate].pure[ConnectionIO])
      (templateRepo.insert _).expects(created).returns(created.pure[ConnectionIO])
      (versionRepo.insert _).expects(first).returns(first.pure[ConnectionIO])
      (templateRepo.findFullById _).expects(created.id).returns(full.pure[ConnectionIO])

      service.create(created, first).map(_ shouldBe Right(full)).unsafeToFuture()
    }

    "reject a label of another project" in {
      val created = template(Some(labelId))

      (labelRepo.findById _).expects(labelId).returns(label(UUID.randomUUID()).pure[ConnectionIO])

      service.create(created, version(created.id)).map(_ shouldBe Left(LabelNotInProject)).unsafeToFuture()
    }

    "reject an unknown label" in {
      val created = template(Some(labelId))

      (labelRepo.findById _)
        .expects(labelId)
        .returns(UnexpectedEnd.raiseError[ConnectionIO, TaskLabel])

      service.create(created, version(created.id)).map(_ shouldBe Left(LabelNotInProject)).unsafeToFuture()
    }

    "reject a label that is linked to another template" in {
      val created = template(Some(labelId))
      val other = template(Some(labelId))

      (labelRepo.findById _).expects(labelId).returns(label().pure[ConnectionIO])
      (templateRepo.findByLabelId _).expects(labelId).returns(other.some.pure[ConnectionIO])

      service.create(created, version(created.id)).map(_ shouldBe Left(LabelAlreadyHasTemplate)).unsafeToFuture()
    }

    "not add a version when the name and body are unchanged" in {
      val existing = template()
      val current = version(existing.id)
      val full = TaskTemplateFull(existing, current, None)

      (templateRepo.findById _).expects(existing.id).returns(existing.pure[ConnectionIO])
      (versionRepo.findLatestByTemplateId _).expects(existing.id).returns(current.pure[ConnectionIO])
      (templateRepo.findFullById _).expects(existing.id).returns(full.pure[ConnectionIO])

      service
        .update(existing.id, current.name, current.body, None, nowAsInstant)
        .map(_ shouldBe Right(full))
        .unsafeToFuture()
    }

    "add a version when the name or the body changed" in {
      val existing = template()
      val current = version(existing.id)
      val changed = current.copy(version = UUID.randomUUID(), body = "new body")
      val full = TaskTemplateFull(existing, changed, None)

      (templateRepo.findById _).expects(existing.id).returns(existing.pure[ConnectionIO])
      (versionRepo.findLatestByTemplateId _).expects(existing.id).returns(current.pure[ConnectionIO])
      (versionRepo.insert _)
        .expects(where((v: TaskTemplateVersion) => v.taskTemplateId == existing.id && v.body == "new body"))
        .returns(changed.pure[ConnectionIO])
      (templateRepo.findFullById _).expects(existing.id).returns(full.pure[ConnectionIO])

      service
        .update(existing.id, current.name, "new body", None, nowAsInstant)
        .map(_ shouldBe Right(full))
        .unsafeToFuture()
    }

    "re-link the label without adding a version" in {
      val existing = template()
      val current = version(existing.id)
      val linked = existing.copy(taskLabelId = Some(labelId))
      val full = TaskTemplateFull(linked, current, Some("feature"))

      (templateRepo.findById _).expects(existing.id).returns(existing.pure[ConnectionIO])
      (labelRepo.findById _).expects(labelId).returns(label().pure[ConnectionIO])
      (templateRepo.findByLabelId _).expects(labelId).returns(none[TaskTemplate].pure[ConnectionIO])
      (templateRepo.updateLabel _).expects(existing.id, Some(labelId)).returns(linked.pure[ConnectionIO])
      (versionRepo.findLatestByTemplateId _).expects(existing.id).returns(current.pure[ConnectionIO])
      (templateRepo.findFullById _).expects(existing.id).returns(full.pure[ConnectionIO])

      service
        .update(existing.id, current.name, current.body, Some(labelId), nowAsInstant)
        .map(_ shouldBe Right(full))
        .unsafeToFuture()
    }

    "keep the label of the template itself when updating" in {
      val existing = template(Some(labelId))
      val current = version(existing.id)
      val full = TaskTemplateFull(existing, current, Some("feature"))

      (templateRepo.findById _).expects(existing.id).returns(existing.pure[ConnectionIO])
      (labelRepo.findById _).expects(labelId).returns(label().pure[ConnectionIO])
      (templateRepo.findByLabelId _).expects(labelId).returns(existing.some.pure[ConnectionIO])
      (versionRepo.findLatestByTemplateId _).expects(existing.id).returns(current.pure[ConnectionIO])
      (templateRepo.findFullById _).expects(existing.id).returns(full.pure[ConnectionIO])

      service
        .update(existing.id, current.name, current.body, Some(labelId), nowAsInstant)
        .map(_ shouldBe Right(full))
        .unsafeToFuture()
    }

    "reject linking a label that has another template when updating" in {
      val existing = template()
      val other = template(Some(labelId))

      (templateRepo.findById _).expects(existing.id).returns(existing.pure[ConnectionIO])
      (labelRepo.findById _).expects(labelId).returns(label().pure[ConnectionIO])
      (templateRepo.findByLabelId _).expects(labelId).returns(other.some.pure[ConnectionIO])

      service
        .update(existing.id, "name", "body", Some(labelId), nowAsInstant)
        .map(_ shouldBe Left(LabelAlreadyHasTemplate))
        .unsafeToFuture()
    }

    "report an unknown template on update, find and delete" in {
      val id = UUID.randomUUID()

      (templateRepo.findById _).expects(id).returns(UnexpectedEnd.raiseError[ConnectionIO, TaskTemplate])
      (templateRepo.findFullById _).expects(id).returns(UnexpectedEnd.raiseError[ConnectionIO, TaskTemplateFull])
      (templateRepo.deleteById _).expects(id).returns(0.pure[ConnectionIO])

      (for {
        updated <- service.update(id, "name", "body", None, nowAsInstant)
        found <- service.findById(id)
        deleted <- service.delete(id)
      } yield {
        updated shouldBe Left(TaskTemplateNotFound)
        found shouldBe Left(TaskTemplateNotFound)
        deleted shouldBe Left(TaskTemplateNotFound)
      }).unsafeToFuture()
    }

    "delete an existing template" in {
      val id = UUID.randomUUID()

      (templateRepo.deleteById _).expects(id).returns(1.pure[ConnectionIO])

      service.delete(id).map(_ shouldBe Right(1)).unsafeToFuture()
    }
  }
}
