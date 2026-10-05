package com.togger.dto

import java.util.UUID

case class CreateProjectLabelReq(name: String, description: String, categoryId: UUID)
case class CreateProjectLabelResp(id: UUID, name: String, description: String, categoryId: UUID)
case class FindProjectLabelResp(id: UUID, name: String, description: String, categoryId: UUID)
case class UpdateProjectLabelResp(id: UUID, name: String, description: String, categoryId: UUID)
