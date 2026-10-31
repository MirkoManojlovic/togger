package com.togger.preview

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.common.{BadRequest, Forbidden, NotFound, PageRequest, Unauthenticated}
import com.togger.dto.login.{AccountInfoDto, ChangePasswordDto, DeviceInfo, LoginDto}
import com.togger.dto.organization.{CreateOrganizationReq, UpdateOrganizationReq}
import com.togger.dto.{CreateProjectReq, UpdateAccountReq, UpdateProjectReq}
import com.togger.ui.accountmanagement.AccountManagementFeServicePreview
import com.togger.ui.auth.{ChangeAccountInfoServicePreview, ChangePasswordServicePreview, LoginServicePreview}
import com.togger.ui.organization.OrganizationServiceFePreview
import com.togger.ui.project.{FindAccountServicePreview, ProjectServicePreview}

class PreviewAccountsDomainTest extends AsyncJsWordSpec {

  private val device = DeviceInfo("test", "127.0.0.1")
  private val firstPage = PageRequest(1, 20)

  private class Services(val env: PreviewTestEnv = new PreviewTestEnv) {
    private val m = env.module
    val login = new LoginServicePreview(m.accounts, env.loginRepo)
    val password = new ChangePasswordServicePreview(m.accounts, env.loginRepo)
    val info = new ChangeAccountInfoServicePreview(m.accounts, env.loginRepo)
    val management = new AccountManagementFeServicePreview(m.context, m.accounts, m.organizations, m.projects)
    val find = new FindAccountServicePreview(m.context, m.accounts)
    val organizations = new OrganizationServiceFePreview(m.context, m.accounts, m.organizations, m.projects)
    val projects = new ProjectServicePreview(m.context, m.accounts, m.organizations, m.projects)
  }

  testNameFromClassName should {

    "log in with the demo credentials and save the session" in { _ =>
      val s = new Services
      s.env
        .runIO(s.login.login(LoginDto(PreviewDemo.Email, PreviewDemo.Password, device)))
        .map { result =>
          result shouldBe Right(())
          s.env.loginRepo.account.map(_.id) shouldBe Some(PreviewDemo.AccountId)
        }
    }

    "log in as a teammate and reject wrong credentials" in { _ =>
      val s = new Services
      for {
        wrong <- s.env.runIO(s.login.login(LoginDto(PreviewDemo.Email, "nope", device)))
        teammate <- s.env.runIO(s.login.login(LoginDto("SAM.rivera@togger.preview", "preview", device)))
      } yield {
        wrong.isLeft shouldBe true
        teammate shouldBe Right(())
        s.env.loginRepo.account.map(_.id) shouldBe Some(PreviewDemo.SecondAccountId)
      }
    }

    "answer unauthenticated without a session" in { _ =>
      val s = new Services
      s.env.run(s.organizations.findByAccountId(PreviewDemo.AccountId, firstPage)).map(_ shouldBe Left(Unauthenticated))
    }

    "change the password only with the right old password" in { _ =>
      val s = new Services
      s.env.loginAsDemo()
      def dto(old: String, confirm: String) =
        ChangePasswordDto(PreviewDemo.AccountId, old, "NewPassw0rd!2345", confirm)
      for {
        wrongOld <- s.env.runIO(s.password.change(dto("bad", "NewPassw0rd!2345")))
        mismatch <- s.env.runIO(s.password.change(dto(PreviewDemo.Password, "other")))
        ok <- s.env.runIO(s.password.change(dto(PreviewDemo.Password, "NewPassw0rd!2345")))
      } yield {
        wrongOld.left.exists(_.isInstanceOf[BadRequest]) shouldBe true
        mismatch.left.exists(_.isInstanceOf[BadRequest]) shouldBe true
        ok.isRight shouldBe true
        s.env.module.accounts.accounts.find(PreviewDemo.AccountId).get.password shouldBe "NewPassw0rd!2345"
      }
    }

    "update the account info, the session and reject a taken email" in { _ =>
      val s = new Services
      s.env.loginAsDemo()
      for {
        taken <- s.env.run(s.info.change(AccountInfoDto("Alex", "Morgan", "sam.rivera@togger.preview")))
        ok <- s.env.run(s.info.change(AccountInfoDto(" Alexa ", "Morgan", "alexa@togger.preview")))
      } yield {
        taken.left.exists(_.isInstanceOf[BadRequest]) shouldBe true
        ok.map(_.firstName) shouldBe Right("Alexa")
        s.env.loginRepo.account.flatMap(_.firstName) shouldBe Some("Alexa")
      }
    }

    "manage accounts as admin and forbid it for others" in { _ =>
      val s = new Services
      s.env.loginAsDemo()
      for {
        page <- s.env.run(s.management.findAll(PageRequest(1, 2)))
        updated <- s.env.run(
          s.management.update(
            UpdateAccountReq(PreviewDemo.SecondAccountId, "Samantha", "Rivera", "sam.rivera@togger.preview", PreviewAccountTypes.AdminId)
          )
        )
        self <- s.env.runEmpty(s.management.deleteById(PreviewDemo.AccountId))
        deleted <- s.env.runEmpty(s.management.deleteById(AccountsPreviewSeed.FifthAccountId))
        _ = s.env.loginAs(AccountsPreviewSeed.ThirdAccountId)
        forbidden <- s.env.run(s.management.findAll(firstPage))
      } yield {
        page.map(p => (p.items.size, p.totalNumberOfItems)) shouldBe Right((2, 6L))
        updated.map(_.accountTypeName) shouldBe Right("admin")
        self.left.exists(_.isInstanceOf[BadRequest]) shouldBe true
        deleted.isRight shouldBe true
        s.env.module.accounts.accounts.find(AccountsPreviewSeed.FifthAccountId) shouldBe None
        forbidden shouldBe Left(Forbidden)
      }
    }

    "search accounts by name or email and require two characters" in { _ =>
      val s = new Services
      s.env.loginAsDemo()
      for {
        byEmail <- s.env.run(s.find.findByFirstNameLastNameOrEmail("rivera", firstPage))
        short <- s.env.run(s.find.findByFirstNameLastNameOrEmail("a", firstPage))
      } yield {
        byEmail.map(_.items.map(_.firstName)) shouldBe Right(List("Sam"))
        short.left.exists(_.isInstanceOf[BadRequest]) shouldBe true
      }
    }

    "create, update, page and delete organizations with members" in { _ =>
      val s = new Services
      s.env.loginAsDemo()
      for {
        created <- s.env.run(
          s.organizations.createOrganization(CreateOrganizationReq("Zeta", "d", "🔥", Seq(PreviewDemo.SecondAccountId)))
        )
        id = created.toOption.get.id
        updated <- s.env.run(
          s.organizations.update(
            UpdateOrganizationReq(id, "Zeta 2", "d", "🔥", Seq(AccountsPreviewSeed.ThirdAccountId), Seq(PreviewDemo.SecondAccountId))
          )
        )
        mine <- s.env.run(s.organizations.findByAccountId(PreviewDemo.AccountId, PageRequest(1, 2)))
        members <- s.env.run(s.organizations.findMembers(id, firstPage))
        _ <- s.env.runEmpty(s.organizations.deleteOrganization(id))
        gone <- s.env.run(s.organizations.findById(id))
      } yield {
        created.map(_.members.size) shouldBe Right(2)
        updated.map(_.name) shouldBe Right("Zeta 2")
        mine.map(p => (p.items.map(_.name), p.totalNumberOfItems)) shouldBe Right((List("Acme Software", "Open Source Corner"), 3L))
        members.map(_.items.map(_.firstName).toSet) shouldBe Right(Set("Alex", "Jordan"))
        gone shouldBe Left(NotFound)
      }
    }

    "create, filter, update and delete projects and cascade from the organization" in { _ =>
      val s = new Services
      s.env.loginAs(PreviewDemo.SecondAccountId)
      for {
        acme <- s.env.run(s.projects.findByOrganizationId(PreviewDemo.OrganizationId, firstPage))
        created <- s.env.run(
          s.projects.createProject(CreateProjectReq(PreviewDemo.OrganizationId, "Alpha", "d", "🅰", Seq(PreviewDemo.AccountId)))
        )
        id = created.toOption.get.id
        noWorkDays <- s.env.run(
          s.projects.updateProject(UpdateProjectReq(id, PreviewDemo.OrganizationId, "Alpha", "d", "🅰", Set.empty, Seq.empty, Seq.empty))
        )
        updated <- s.env.run(
          s.projects.updateProject(
            UpdateProjectReq(id, PreviewDemo.OrganizationId, "Alpha 2", "d", "🅰", Set(1, 2), Seq.empty, Seq(PreviewDemo.AccountId))
          )
        )
        members <- s.env.run(s.projects.findAllMembers(id))
        missing <- s.env.run(s.projects.findById(java.util.UUID.randomUUID()))
        _ = s.env.loginAsDemo()
        _ <- s.env.runEmpty(s.organizations.deleteOrganization(PreviewDemo.OrganizationId))
        remaining <- s.env.run(s.projects.searchByLabels(Seq.empty, firstPage))
      } yield {
        acme.map(_.items.map(_.name)) shouldBe Right(List("Acme Platform", "Mobile App"))
        created.map(_.members.size) shouldBe Right(2)
        noWorkDays.left.exists(_.isInstanceOf[BadRequest]) shouldBe true
        updated.map(p => (p.name, p.workDays)) shouldBe Right(("Alpha 2", Set(1, 2)))
        members.map(_.map(_.firstName)) shouldBe Right(Seq("Sam"))
        missing shouldBe Left(NotFound)
        remaining.map(_.items.map(_.name)) shouldBe Right(List("Docs Site"))
      }
    }
  }
}
