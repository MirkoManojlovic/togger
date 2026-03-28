package com.togger.login

import java.util.UUID

trait SessionTokenFactory {
  def generateSessionToken: String

}
class SessionTokenFactoryImpl extends SessionTokenFactory {
  def generateSessionToken: String = UUID.randomUUID().toString
}
