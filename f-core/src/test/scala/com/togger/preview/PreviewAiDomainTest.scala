package com.togger.preview

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.common.BadRequest
import com.togger.dto._
import com.togger.ui.tgaiaction.TgAiActionFeServicePreview
import com.togger.ui.tgprompttemplate.TgPromptTemplateFeServicePreview

import java.util.UUID

class PreviewAiDomainTest extends AsyncJsWordSpec {

  private class Services(val env: PreviewTestEnv = new PreviewTestEnv) {
    private val m = env.module
    val prompts = new TgPromptTemplateFeServicePreview(m.context, m.ai)
    val actions = new TgAiActionFeServicePreview(m.context, m.projects, m.ai)
    env.loginAsDemo()
  }

  private def isBadRequest(result: Either[_, _]): Boolean = result.left.exists(_.isInstanceOf[BadRequest])

  testNameFromClassName should {

    "list only the prompts of the caller, newest first" in { _ =>
      val s = new Services
      for {
        mine <- s.env.run(s.prompts.findAllTemplates)
        _ = s.env.loginAs(PreviewDemo.SecondAccountId)
        theirs <- s.env.run(s.prompts.findAllTemplates)
      } yield {
        mine.map(_.size) shouldBe Right(3)
        mine.map(_.map(_.title)) shouldBe Right(List("Executive summary", "Risks and blockers", "Weekly status report"))
        theirs.map(_.map(_.title)) shouldBe Right(List("Sprint recap"))
      }
    }

    "version prompts and refuse to delete the last version or a foreign prompt" in { _ =>
      val s = new Services
      for {
        created <- s.env.run(s.prompts.createTgPromptTemplate(CreateTgPromptTemplateReq("Retro", "v1")))
        id = created.toOption.get.id
        same <- s.env.run(s.prompts.saveNewVersion(NewTgPromptTemplateVersionReq(id, "Retro", "v1")))
        changed <- s.env.run(s.prompts.saveNewVersion(NewTgPromptTemplateVersionReq(id, "Retro", "v2")))
        versions <- s.env.run(s.prompts.listAllVersions(id))
        deleteOld <- s.env.runEmpty(s.prompts.deleteVersion(versions.toOption.get.last.version))
        deleteLast <- s.env.runEmpty(s.prompts.deleteVersion(versions.toOption.get.head.version))
        _ = s.env.loginAs(PreviewDemo.SecondAccountId)
        foreign <- s.env.run(s.prompts.findTgPromptTemplateById(id))
      } yield {
        same.map(_.latestVersion) shouldBe created.map(_.latestVersion)
        changed.map(_.prompt) shouldBe Right("v2")
        versions.map(_.map(_.prompt)) shouldBe Right(List("v2", "v1"))
        deleteOld.isRight shouldBe true
        isBadRequest(deleteLast) shouldBe true
        isBadRequest(foreign) shouldBe true
      }
    }

    "detach a deleted prompt from its actions" in { _ =>
      val s = new Services
      val promptId = PreviewSeedIds.id(PreviewSeedIds.PromptTemplate, 1)
      for {
        deleted <- s.env.runEmpty(s.prompts.deleteTgPromptTemplateById(promptId))
        action <- s.env.run(s.actions.findById(PreviewSeedIds.id(PreviewSeedIds.AiAction, 1)))
      } yield {
        deleted.isRight shouldBe true
        action.map(_.tgPromptTemplateId) shouldBe Right(None)
      }
    }

    "list the models and endpoints" in { _ =>
      val s = new Services
      for {
        models <- s.env.run(s.actions.listModels)
        endpoints <- s.env.run(s.actions.listEndpoints)
      } yield {
        models.map(_.map(_.id)).toOption.get should contain("gpt-4.1")
        endpoints shouldBe Right(List(TgAiEndpointDto(0, "milestone-summary")))
      }
    }

    "create, update and delete actions with unique names per account" in { _ =>
      val s = new Services
      val promptId = PreviewSeedIds.id(PreviewSeedIds.PromptTemplate, 1)
      def create(name: String, prompt: Option[UUID] = None, endpoints: List[Int] = List(0)) =
        s.actions.create(CreateTgAiActionReq(name, "p", prompt, "gpt-4.1", endpoints))
      for {
        seeded <- s.env.run(s.actions.findEvery)
        duplicate <- s.env.run(create("Weekly summary"))
        badEndpoint <- s.env.run(create("Other", endpoints = List(42)))
        foreignPrompt <- s.env.run(create("Other", Some(PreviewSeedIds.id(PreviewSeedIds.PromptTemplate, 4))))
        created <- s.env.run(create("Brand new", Some(promptId)))
        id = created.toOption.get.id
        updated <- s.env.run(s.actions.update(UpdateTgAiActionReq(id, "Brand newer", "p2", None, "gpt-4o", List(0))))
        renameClash <- s.env.run(s.actions.update(UpdateTgAiActionReq(id, "Quick recap", "p2", None, "gpt-4o", List(0))))
        deleted <- s.env.runEmpty(s.actions.deleteById(id))
        gone <- s.env.run(s.actions.findById(id))
        _ = s.env.loginAs(PreviewDemo.SecondAccountId)
        foreign <- s.env.run(s.actions.findById(PreviewSeedIds.id(PreviewSeedIds.AiAction, 1)))
      } yield {
        seeded.map(_.map(_.name)) shouldBe Right(List("Quick recap", "Risk review", "Executive brief", "Weekly summary"))
        isBadRequest(duplicate) shouldBe true
        isBadRequest(badEndpoint) shouldBe true
        isBadRequest(foreignPrompt) shouldBe true
        updated.map(a => (a.name, a.model, a.tgPromptTemplateId)) shouldBe Right(("Brand newer", "gpt-4o", None))
        isBadRequest(renameClash) shouldBe true
        deleted.isRight shouldBe true
        isBadRequest(gone) shouldBe true
        isBadRequest(foreign) shouldBe true
      }
    }

    "manage the defaults per project and endpoint" in { _ =>
      val s = new Services
      val weekly = PreviewSeedIds.id(PreviewSeedIds.AiAction, 1)
      val risk = PreviewSeedIds.id(PreviewSeedIds.AiAction, 3)
      for {
        seeded <- s.env.run(s.actions.findDefaults(PreviewDemo.ProjectId))
        replaced <- s.env.run(s.actions.setDefault(SetTgAiActionDefaultReq(PreviewDemo.ProjectId, 0, risk)))
        afterReplace <- s.env.run(s.actions.findDefaults(PreviewDemo.ProjectId))
        notBound <- s.env.run(s.actions.setDefault(SetTgAiActionDefaultReq(PreviewDemo.ProjectId, 7, weekly)))
        notMember <- s.env.run(s.actions.setDefault(SetTgAiActionDefaultReq(UUID.randomUUID(), 0, weekly)))
        cleared <- s.env.runEmpty(s.actions.clearDefault(PreviewDemo.ProjectId, 0))
        afterClear <- s.env.run(s.actions.findDefaults(PreviewDemo.ProjectId))
        _ <- s.env.run(s.actions.setDefault(SetTgAiActionDefaultReq(PreviewDemo.ProjectId, 0, weekly)))
        _ <- s.env.runEmpty(s.actions.deleteById(weekly))
        afterDelete <- s.env.run(s.actions.findDefaults(PreviewDemo.ProjectId))
      } yield {
        seeded.map(_.map(_.actionId)) shouldBe Right(List(weekly))
        replaced.map(_.actionId) shouldBe Right(risk)
        afterReplace.map(_.map(_.actionId)) shouldBe Right(List(risk))
        isBadRequest(notBound) shouldBe true
        isBadRequest(notMember) shouldBe true
        cleared.isRight shouldBe true
        afterClear shouldBe Right(Nil)
        afterDelete shouldBe Right(Nil)
      }
    }
  }
}
