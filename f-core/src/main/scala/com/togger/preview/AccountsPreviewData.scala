package com.togger.preview

import com.togger.dto.{AccountTypeDto, SearchAccountDto}
import io.circe.generic.auto._

import java.util.UUID

/** Passwords are stored in plain text on purpose: the preview has no backend and no secrets. */
case class PreviewAccount(
  id: UUID,
  firstName: String,
  lastName: String,
  email: String,
  password: String,
  accountTypeId: UUID,
  enabled: Boolean = true
) {
  def toSearchDto: SearchAccountDto = SearchAccountDto(id, firstName, lastName, email, accountTypeId)
}

object PreviewAccountTypes {
  val AdminId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000a01")
  val BusinessId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000a02")

  val all: List[AccountTypeDto] = List(AccountTypeDto(AdminId, "admin"), AccountTypeDto(BusinessId, "business"))
}

/** The preview accounts, shared by the auth services and by every domain that needs to show members or assignees. */
class AccountsPreviewData(db: PreviewDb) {

  val accounts: PreviewCollection[PreviewAccount] = db.collection[PreviewAccount](AccountsPreviewData.Name, _.id)

  def byEmail(email: String): Option[PreviewAccount] =
    accounts.all.find(_.email.equalsIgnoreCase(email.trim))

  def isAdmin(accountId: UUID): Boolean =
    accounts.find(accountId).exists(_.accountTypeId == PreviewAccountTypes.AdminId)

  def searchDtos(accountIds: Seq[UUID]): List[SearchAccountDto] =
    accounts.filter(account => accountIds.contains(account.id)).sorted(AccountsPreviewData.byName).map(_.toSearchDto)
}

object AccountsPreviewData {
  val Name = "accounts"

  val byName: Ordering[PreviewAccount] =
    Ordering.by(account => (account.lastName.toLowerCase, account.firstName.toLowerCase, account.id.toString))
}
