package com.togger.demo

import java.util.UUID

object DemoAccountsData {
  val ThirdAccountId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000003")
  val FourthAccountId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000004")
  val FifthAccountId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000005")

  val accounts: List[DemoAccount] = List(
    DemoAccount(
      DemoIdentity.AdminAccountId,
      "Admin",
      "Account",
      DemoIdentity.AdminEmail,
      DemoIdentity.AdminPassword,
      admin = true
    ),
    DemoAccount(DemoIdentity.AccountId, "Alex", "Morgan", DemoIdentity.Email, DemoIdentity.Password, admin = true),
    DemoAccount(DemoIdentity.SecondAccountId, "Sam", "Rivera", "sam.rivera@togger.preview", "preview", admin = false),
    DemoAccount(ThirdAccountId, "Jordan", "Lee", "jordan.lee@togger.preview", "preview", admin = false),
    DemoAccount(FourthAccountId, "Priya", "Nair", "priya.nair@togger.preview", "preview", admin = false),
    DemoAccount(FifthAccountId, "Tomas", "Novak", "tomas.novak@togger.preview", "preview", admin = false)
  )
}
