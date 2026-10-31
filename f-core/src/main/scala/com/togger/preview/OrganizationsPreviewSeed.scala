package com.togger.preview

import java.util.UUID

/** Seeds the organizations, the projects and their members; needs the accounts to be seeded first. */
class OrganizationsPreviewSeed(organizations: OrganizationsPreviewData, projects: ProjectsPreviewData)
    extends PreviewSeeder {
  import OrganizationsPreviewSeed._

  override def seed(): Unit = {
    organizations.organizations.upsert(
      PreviewOrganization(
        PreviewDemo.OrganizationId,
        "Acme Software",
        "The product company behind the Acme platform.",
        "🏢"
      )
    )
    organizations.organizations.upsert(
      PreviewOrganization(SideOrganizationId, "Open Source Corner", "Community work done outside of Acme.", "🌱")
    )
    organizations.addMembers(PreviewDemo.OrganizationId, acmeMembers)
    organizations.addMembers(SideOrganizationId, List(PreviewDemo.AccountId, PreviewDemo.SecondAccountId, PreviewDemo.AdminAccountId))

    projectsSeed.foreach { case (project, members) =>
      projects.projects.upsert(project)
      projects.addMembers(project.id, members)
    }
  }
}

object OrganizationsPreviewSeed {
  val SideOrganizationId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000102")
  val SideProjectId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000203")

  private val weekdays = Set(1, 2, 3, 4, 5)

  private val acmeMembers = List(
    PreviewDemo.AdminAccountId,
    PreviewDemo.AccountId,
    PreviewDemo.SecondAccountId,
    AccountsPreviewSeed.ThirdAccountId,
    AccountsPreviewSeed.FourthAccountId,
    AccountsPreviewSeed.FifthAccountId
  )

  private val projectsSeed: List[(PreviewProject, List[UUID])] = List(
    PreviewProject(
      PreviewDemo.ProjectId,
      PreviewDemo.OrganizationId,
      "Acme Platform",
      "The main web platform: API, web app and background jobs.",
      "🚀",
      weekdays
    ) -> acmeMembers,
    PreviewProject(
      PreviewDemo.SecondProjectId,
      PreviewDemo.OrganizationId,
      "Mobile App",
      "Companion mobile app for iOS and Android.",
      "📱",
      weekdays
    ) -> List(PreviewDemo.AdminAccountId, PreviewDemo.AccountId, PreviewDemo.SecondAccountId, AccountsPreviewSeed.FourthAccountId),
    PreviewProject(
      SideProjectId,
      SideOrganizationId,
      "Docs Site",
      "Community documentation and tutorials.",
      "📚",
      Set(1, 2, 3, 4, 5, 6)
    ) -> List(PreviewDemo.AdminAccountId, PreviewDemo.AccountId, PreviewDemo.SecondAccountId)
  )
}
