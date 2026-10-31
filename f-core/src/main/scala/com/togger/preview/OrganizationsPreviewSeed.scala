package com.togger.preview

import com.togger.demo.{DemoOrganization, DemoProject}

/** Seeds the organizations, the projects and their members; needs the accounts to be seeded first. */
class OrganizationsPreviewSeed(
  organizations: OrganizationsPreviewData,
  projects: ProjectsPreviewData,
  demoOrganizations: List[DemoOrganization],
  demoProjects: List[DemoProject]
) extends PreviewSeeder {

  override def seed(): Unit = {
    demoOrganizations.foreach { organization =>
      organizations.organizations.upsert(
        PreviewOrganization(organization.id, organization.name, organization.description, organization.icon)
      )
      organizations.addMembers(organization.id, organization.memberIds)
    }
    demoProjects.foreach { project =>
      projects.projects.upsert(
        PreviewProject(
          project.id,
          project.organizationId,
          project.name,
          project.description,
          project.icon,
          project.workDays,
          project.projectLabelIds
        )
      )
      projects.addMembers(project.id, project.memberIds)
    }
  }
}
