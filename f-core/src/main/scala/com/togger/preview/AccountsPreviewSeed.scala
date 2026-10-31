package com.togger.preview

import java.util.UUID

class AccountsPreviewSeed(data: AccountsPreviewData) extends PreviewSeeder {

  override def seed(): Unit =
    AccountsPreviewSeed.accounts.foreach(data.accounts.upsert)
}

object AccountsPreviewSeed {
  val ThirdAccountId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000003")
  val FourthAccountId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000004")
  val FifthAccountId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000005")

  val accounts: List[PreviewAccount] = List(
    PreviewAccount(
      PreviewDemo.AdminAccountId,
      "Admin",
      "Account",
      PreviewDemo.AdminEmail,
      PreviewDemo.AdminPassword,
      PreviewAccountTypes.AdminId
    ),
    PreviewAccount(
      PreviewDemo.AccountId,
      "Alex",
      "Morgan",
      PreviewDemo.Email,
      PreviewDemo.Password,
      PreviewAccountTypes.AdminId
    ),
    PreviewAccount(
      PreviewDemo.SecondAccountId,
      "Sam",
      "Rivera",
      "sam.rivera@togger.preview",
      "preview",
      PreviewAccountTypes.BusinessId
    ),
    PreviewAccount(ThirdAccountId, "Jordan", "Lee", "jordan.lee@togger.preview", "preview", PreviewAccountTypes.BusinessId),
    PreviewAccount(FourthAccountId, "Priya", "Nair", "priya.nair@togger.preview", "preview", PreviewAccountTypes.BusinessId),
    PreviewAccount(FifthAccountId, "Tomas", "Novak", "tomas.novak@togger.preview", "preview", PreviewAccountTypes.BusinessId)
  )
}
