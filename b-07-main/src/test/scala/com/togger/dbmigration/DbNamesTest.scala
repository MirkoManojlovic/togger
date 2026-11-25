package com.togger.dbmigration

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DbNamesTest extends AnyWordSpec with Matchers {

  "SemVer.parse" should {
    "read the release part of a version" in {
      SemVer.parse("0.5.0") shouldBe Some(SemVer(0, 5, 0))
      SemVer.parse("0.5.0+3-abcd1234") shouldBe Some(SemVer(0, 5, 0))
      SemVer.parse("0.5.0+3-abcd1234+20261125-1030") shouldBe Some(SemVer(0, 5, 0))
      SemVer.parse("v1.12.3") shouldBe Some(SemVer(1, 12, 3))
    }

    "reject a version that is not a release" in {
      SemVer.parse("ci") shouldBe None
      SemVer.parse("") shouldBe None
    }

    "order numerically" in {
      (SemVer(0, 10, 0) > SemVer(0, 9, 0)) shouldBe true
    }
  }

  "DbNames.prodName" should {
    "be versioned for a release version" in {
      DbNames.prodName("0.0.5") shouldBe "togger-local-0.0.5"
      DbNames.prodName("0.5.0+3-abcd") shouldBe "togger-local-0.5.0"
    }

    "be the unversioned name otherwise" in {
      DbNames.prodName("ci") shouldBe "togger-local"
    }
  }

  "DbNames.previousOf" should {
    val current = SemVer(0, 6, 0)

    "take the highest older versioned database" in {
      val existing = List("postgres", "togger-local", "togger-local-0.4.0", "togger-local-0.5.0", "togger-local-0.7.0")
      DbNames.previousOf(current, existing) shouldBe Some("togger-local-0.5.0")
    }

    "compare versions numerically" in {
      val existing = List("togger-local-0.9.0", "togger-local-0.10.0")
      DbNames.previousOf(SemVer(0, 11, 0), existing) shouldBe Some("togger-local-0.10.0")
    }

    "fall back to the legacy database" in {
      DbNames.previousOf(current, List("postgres", "togger-local", "togger-local-0.7.0")) shouldBe Some("togger-local")
    }

    "ignore the development database" in {
      DbNames.previousOf(current, List("togger-local-development")) shouldBe None
    }

    "be none without any older database" in {
      DbNames.previousOf(current, List("postgres")) shouldBe None
    }
  }
}
