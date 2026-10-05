package com.togger.dto

import java.util.UUID

case class CreateProjectLabelCategoryReq(name: String)
case class CreateProjectLabelCategoryResp(id: UUID, name: String)
case class FindProjectLabelCategoryResp(id: UUID, name: String)
case class UpdateProjectLabelCategoryResp(id: UUID, name: String)
