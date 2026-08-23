package com.togger.ui.taskloadconfig

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto.taskloadconfig.{CreateTaskLoadConfigReq, TaskLoadConfigResp, UpdateTaskLoadConfigReq}
import com.togger.taskloadconfig.TaskLoadConfigEndpoints

import java.util.UUID

trait TaskLoadConfigFeService {
  def create(dto: CreateTaskLoadConfigReq): TgRespIO[TaskLoadConfigResp]

  def findById(id: UUID): TgRespIO[TaskLoadConfigResp]

  def update(dto: UpdateTaskLoadConfigReq): TgRespIO[TaskLoadConfigResp]

  def deleteById(id: UUID): TgRespIO[TgEmptyBody]

  def findByProjectId(projectId: UUID): TgRespIO[Seq[TaskLoadConfigResp]]

  def findByAccountId(accountId: UUID): TgRespIO[Seq[TaskLoadConfigResp]]
}

class TaskLoadConfigFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends TaskLoadConfigFeService {

  private val taskLoadConfigEndpoints = new TaskLoadConfigEndpoints(securedEndpoint)

  override def create(dto: CreateTaskLoadConfigReq): TgRespIO[TaskLoadConfigResp] =
    protectedBackendClient.protectedHttpCall(taskLoadConfigEndpoints.saveEndpoint)(dto)

  override def findById(id: UUID): TgRespIO[TaskLoadConfigResp] =
    protectedBackendClient.protectedHttpCall(taskLoadConfigEndpoints.findByIdEndpoint)(id)

  override def update(dto: UpdateTaskLoadConfigReq): TgRespIO[TaskLoadConfigResp] =
    protectedBackendClient.protectedHttpCall(taskLoadConfigEndpoints.updateEndpoint)(dto)

  override def deleteById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(taskLoadConfigEndpoints.deleteByIdEndpoint)(id)

  override def findByProjectId(projectId: UUID): TgRespIO[Seq[TaskLoadConfigResp]] =
    protectedBackendClient.protectedHttpCall(taskLoadConfigEndpoints.findByProjectIdEndpoint)(projectId)

  override def findByAccountId(accountId: UUID): TgRespIO[Seq[TaskLoadConfigResp]] =
    protectedBackendClient.protectedHttpCall(taskLoadConfigEndpoints.findByAccountIdEndpoint)(accountId)
}
