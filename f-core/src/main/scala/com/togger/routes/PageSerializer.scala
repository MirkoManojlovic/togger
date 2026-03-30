package com.togger.routes

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.parser.decode
import io.circe.syntax._

class PageSerializer {

  implicit val genDevConfig: Configuration = Configuration.default

  def serialize(page: FrontendPage): String = page.asJson.noSpaces

  def deSerialize(pageAsString: String): FrontendPage =
    decode[FrontendPage](pageAsString).fold(e => throw e, identity)
}
