package com.togger.dto

import java.util.UUID

case class CreateTechStackReq(name: String, description: String, categoryId: UUID)
case class CreateTechStackResp(id: UUID, name: String, description: String, categoryId: UUID)
case class FindTechStackResp(id: UUID, name: String, description: String, categoryId: UUID)
case class UpdateTechStackResp(id: UUID, name: String, description: String, categoryId: UUID)
