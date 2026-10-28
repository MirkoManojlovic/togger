package com.togger.ai

import doobie.ConnectionIO

case class AiEndpointRow(id: Int, name: String)

trait AiEndpointRepo {

  def insert(row: AiEndpointRow): ConnectionIO[AiEndpointRow]

  def findAll: ConnectionIO[List[AiEndpointRow]]

}
