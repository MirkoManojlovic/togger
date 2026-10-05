package com.togger.dto

import com.togger.techstack.TechStackCategory

import java.util.UUID

case class CreateTechStackReq(name: String, description: String, category: TechStackCategory)
case class CreateTechStackResp(id: UUID, name: String, description: String, category: TechStackCategory)
case class FindTechStackResp(id: UUID, name: String, description: String, category: TechStackCategory)
case class UpdateTechStackResp(id: UUID, name: String, description: String, category: TechStackCategory)
