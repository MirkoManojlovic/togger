package com.togger.preview

import com.togger.demo.DemoAccount

class AccountsPreviewSeed(data: AccountsPreviewData, demoAccounts: List[DemoAccount]) extends PreviewSeeder {

  override def seed(): Unit =
    demoAccounts.map(toPreview).foreach(data.accounts.upsert)

  private def toPreview(account: DemoAccount): PreviewAccount =
    PreviewAccount(
      account.id,
      account.firstName,
      account.lastName,
      account.email,
      account.password,
      if (account.admin) PreviewAccountTypes.AdminId else PreviewAccountTypes.BusinessId
    )
}
