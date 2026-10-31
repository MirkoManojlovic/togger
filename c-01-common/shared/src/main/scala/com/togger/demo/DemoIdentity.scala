package com.togger.demo

import java.util.UUID

/** Fixed ids and credentials of the seeded demo data, so seeders of different domains can reference each other. */
object DemoIdentity {
  val Email = "demo@togger.preview"
  val Password = "demo"
  val AdminEmail = "admin@email.com"
  val AdminPassword = "A!good_1_password_is_good"
  val AdminAccountId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000006")
  val AccountId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000001")
  val SecondAccountId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000002")
  val OrganizationId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000101")
  val ProjectId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000201")
  val SecondProjectId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000202")
  val SideOrganizationId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000102")
  val SideProjectId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000203")
}
