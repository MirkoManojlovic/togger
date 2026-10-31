package com.togger.demo

import java.util.UUID

object DemoOrganizationsData {
  /** The projects in the order the per project seed data (statuses, labels, tasks) indexes them. */
  val projectIds: List[UUID] = List(DemoIdentity.ProjectId, DemoIdentity.SecondProjectId, DemoIdentity.SideProjectId)

  private val weekdays = Set(1, 2, 3, 4, 5)

  private val acmeMembers = List(
    DemoIdentity.AdminAccountId,
    DemoIdentity.AccountId,
    DemoIdentity.SecondAccountId,
    DemoAccountsData.ThirdAccountId,
    DemoAccountsData.FourthAccountId,
    DemoAccountsData.FifthAccountId
  )

  val organizations: List[DemoOrganization] = List(
    DemoOrganization(
      DemoIdentity.OrganizationId,
      "Acme Software",
      "The product company behind the Acme platform.",
      "🏢",
      acmeMembers
    ),
    DemoOrganization(
      DemoIdentity.SideOrganizationId,
      "Open Source Corner",
      "Community work done outside of Acme.",
      "🌱",
      List(DemoIdentity.AccountId, DemoIdentity.SecondAccountId, DemoIdentity.AdminAccountId)
    )
  )

  val projects: List[DemoProject] = List(
    DemoProject(
      DemoIdentity.ProjectId,
      DemoIdentity.OrganizationId,
      "Acme Platform",
      "The main web platform: API, web app and background jobs.",
      "🚀",
      weekdays,
      acmeMembers,
      DemoProjectLabelsData.projectLabelIds(DemoIdentity.ProjectId)
    ),
    DemoProject(
      DemoIdentity.SecondProjectId,
      DemoIdentity.OrganizationId,
      "Mobile App",
      "Companion mobile app for iOS and Android.",
      "📱",
      weekdays,
      List(
        DemoIdentity.AdminAccountId,
        DemoIdentity.AccountId,
        DemoIdentity.SecondAccountId,
        DemoAccountsData.FourthAccountId
      ),
      DemoProjectLabelsData.projectLabelIds(DemoIdentity.SecondProjectId)
    ),
    DemoProject(
      DemoIdentity.SideProjectId,
      DemoIdentity.SideOrganizationId,
      "Docs Site",
      "Community documentation and tutorials.",
      "📚",
      Set(1, 2, 3, 4, 5, 6),
      List(DemoIdentity.AdminAccountId, DemoIdentity.AccountId, DemoIdentity.SecondAccountId),
      DemoProjectLabelsData.projectLabelIds(DemoIdentity.SideProjectId)
    )
  )
}
