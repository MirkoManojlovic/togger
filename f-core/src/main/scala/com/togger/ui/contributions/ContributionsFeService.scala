package com.togger.ui.contributions

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.contribution.ContributionEndpoints
import com.togger.dto.{ContributionsDto, ContributionsFilter}

import java.util.UUID

trait ContributionsFeService {
  def findByProjectId(projectId: UUID, filter: ContributionsFilter): TgRespIO[ContributionsDto]
}

class ContributionsFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends ContributionsFeService {
  private val contributionEndpoints = new ContributionEndpoints(securedEndpoint)

  override def findByProjectId(projectId: UUID, filter: ContributionsFilter): TgRespIO[ContributionsDto] =
    protectedBackendClient.protectedHttpCall(contributionEndpoints.findByProjectIdEndpoint)((projectId, filter))
}
