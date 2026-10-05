package com.togger.dto

import java.util.UUID

case class CreateTechStackCategoryReq(name: String)
case class CreateTechStackCategoryResp(id: UUID, name: String)
case class FindTechStackCategoryResp(id: UUID, name: String)
case class UpdateTechStackCategoryResp(id: UUID, name: String)
