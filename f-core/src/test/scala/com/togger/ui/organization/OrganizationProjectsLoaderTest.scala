package com.togger.ui.organization

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.suites.AsyncJsWordSpec
import com.togger.common.{BadRequest, PageRequest, PaginatedResponse, TgJsonSuccess}
import com.togger.dto._
import com.togger.dto.organization.{CreateOrganizationReq, FindOrganizationResp, UpdateOrganizationReq}
import com.togger.ui.project.ProjectService

import java.util.UUID
import scala.collection.mutable

class OrganizationProjectsLoaderTest extends AsyncJsWordSpec {

  private def organization(index: Int) =
    FindOrganizationResp(UUID.randomUUID(), f"Organization $index%03d", "", "🏢", Seq.empty)

  private def project(organizationId: UUID, index: Int) =
    FindProjectResp(UUID.randomUUID(), organizationId, f"Project $index%03d", "", "📁", Set(1, 2, 3, 4, 5), Seq.empty)

  private def pageOf[A](items: List[A], request: PageRequest): TgRespIO[PaginatedResponse[A]] =
    TgJsonSuccess.justBodyRightIO(
      PaginatedResponse.of(
        request.page,
        request.size,
        items.slice((request.page - 1) * request.size, request.page * request.size),
        items.size.toLong
      )
    )

  private class StubOrganizations(organizations: List[FindOrganizationResp]) extends OrganizationServiceFe {
    val requested = mutable.Buffer.empty[PageRequest]
    override def findByAccountId(accountId: UUID, page: PageRequest) = {
      requested += page
      pageOf(organizations, page)
    }
    override def createOrganization(organization: CreateOrganizationReq) = ???
    override def findAll(page: PageRequest) = ???
    override def findById(id: UUID) = ???
    override def update(dto: UpdateOrganizationReq) = ???
    override def findMembers(organizationId: UUID, page: PageRequest) = ???
  }

  private class StubProjects(projectsByOrganization: Map[UUID, List[FindProjectResp]], failFor: Set[UUID] = Set.empty)
      extends ProjectService {
    val requested = mutable.Buffer.empty[(UUID, PageRequest)]
    override def findByOrganizationId(organizationId: UUID, page: PageRequest) = {
      requested += organizationId -> page
      if (failFor.contains(organizationId)) IO.pure(Left(BadRequest(Set.empty)))
      else pageOf(projectsByOrganization.getOrElse(organizationId, List.empty), page)
    }
    override def createProject(dto: CreateProjectReq) = ???
    override def updateProject(dto: UpdateProjectReq) = ???
    override def findById(id: UUID) = ???
    override def findMembers(projectId: UUID, page: PageRequest) = ???
    override def searchByLabels(projectLabelIds: Seq[UUID], page: PageRequest) = ???
  }

  testNameFromClassName should {

    "read every page of organizations, then every page of the projects of each" in { _ =>
      val organizations = (1 to 150).toList.map(organization)
      val projectsByOrganization = Map(
        organizations(0).id -> (1 to 130).toList.map(project(organizations(0).id, _)),
        organizations(1).id -> List(project(organizations(1).id, 1))
      )
      val orgService = new StubOrganizations(organizations)
      val projectService = new StubProjects(projectsByOrganization)

      new OrganizationProjectsLoader(orgService, projectService)
        .loadForAccount(UUID.randomUUID())
        .unsafeToFuture()
        .map { result =>
          val loaded = result.toOption.flatMap(_.body).getOrElse(Seq.empty)
          orgService.requested.toList shouldBe List(PageRequest(1, 100), PageRequest(2, 100))
          loaded.map(_.id) shouldBe organizations.map(_.id)
          loaded.head.projects.map(_.id) shouldBe projectsByOrganization(organizations(0).id).map(_.id)
          loaded(1).projects.map(_.name) shouldBe List("Project 001")
          loaded(2).projects shouldBe empty
          projectService.requested.count(_._1 == organizations(0).id) shouldBe 2
          projectService.requested.map(_._1).distinct.size shouldBe 150
        }
    }

    "carry the work days of a project" in { _ =>
      val org = organization(1)
      val orgService = new StubOrganizations(List(org))
      val projectService = new StubProjects(Map(org.id -> List(project(org.id, 1))))

      new OrganizationProjectsLoader(orgService, projectService)
        .loadForAccount(UUID.randomUUID())
        .unsafeToFuture()
        .map(_.toOption.flatMap(_.body).map(_.head.projects.head.workDays) shouldBe Some(Set(1, 2, 3, 4, 5)))
    }

    "fail when the projects of an organization cannot be read" in { _ =>
      val organizations = List(organization(1), organization(2))
      val projectService = new StubProjects(Map.empty, failFor = Set(organizations(1).id))

      new OrganizationProjectsLoader(new StubOrganizations(organizations), projectService)
        .loadForAccount(UUID.randomUUID())
        .unsafeToFuture()
        .map(_ shouldBe Left(BadRequest(Set.empty)))
    }
  }
}
