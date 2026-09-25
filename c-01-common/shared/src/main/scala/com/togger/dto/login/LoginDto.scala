package com.togger.dto.login

import java.util.UUID

case class LoginDto(
  email: String,
  password: String,
  deviceInfo: DeviceInfo
) {
  override def toString: String =
    s"LoginDto($email, ***, $deviceInfo)"
}

case class DeviceInfo(
  browser: String,
  ipAddress: String
)

case class LoginResponseDto(
  accountId: UUID,
  sessionId: String,
  firstName: String,
  lastName: String,
  email: String
)

case class ExpireSessionResult(deleted: Long, inDb: Long)

case class LogoutEveryoneDto(numberOfSessionsDeleted: Int)

case class ChangePasswordDto(
  accountId: UUID,
  oldPassword: String,
  newPassword: String,
  confirmNewPassword: String
) {

  override def toString: String =
    s"ChangePasswordDto($accountId, ***, ***, ***))"

  val passwordsMatch: Boolean = newPassword == confirmNewPassword

}
