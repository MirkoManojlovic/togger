package com.togger.common

import com.togger.common.AccountSearchTerm.SearchTermTooShort
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AccountSearchTermTest extends AnyWordSpec with Matchers {

  "AccountSearchTerm.validate" should {
    "trim a long enough term" in {
      AccountSearchTerm.validate("  bird ") shouldBe Right("bird")
    }
    "accept a term of exactly the minimum length" in {
      AccountSearchTerm.validate("ab") shouldBe Right("ab")
    }
    "refuse an empty, blank or too short term" in {
      List("", "   ", "a", " a ").foreach { term =>
        AccountSearchTerm.validate(term) shouldBe Left(SearchTermTooShort(AccountSearchTerm.MinLength))
      }
    }
  }
}
