package com.togger.task

import com.togger.dto.CreateLinkReq
import com.togger.dto.UpdateLinkReq
import com.togger.task.LinkValidation.LinkUrlNotAllowed
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LinkValidationTest extends AnyWordSpec with Matchers with EitherValues {

  "LinkValidation.validate" should {

    "accept web addresses, bare addresses and custom schemes" in {
      List("https://example.com", "example.com/x", "localhost:3000", "togger://task/1", "ftp://a.b", "mailto:a@b.com")
        .foreach(url => LinkValidation.validate("name", url) shouldBe Right(()))
    }

    "reject the unsafe schemes" in {
      List(
        "javascript:alert(1)",
        " JavaScript:x",
        "data:text/html,x",
        "file:///etc/passwd",
        "vbscript:x",
        "ms-msdt://x"
      )
        .foreach(url => LinkValidation.validate("name", url).left.value.head shouldBe LinkUrlNotAllowed)
    }
  }

  "LinkValidation.safeHref" should {

    "keep a scheme as typed and open a bare address as https" in {
      LinkValidation.safeHref(" togger://task/1 ") shouldBe Some("togger://task/1")
      LinkValidation.safeHref("example.com/x") shouldBe Some("https://example.com/x")
      LinkValidation.safeHref("javascript:x") shouldBe None
      LinkValidation.safeHref("  ") shouldBe None
    }
  }

  "LinkValidation.validateAll" should {

    "reject an unsafe address of a link to create and of a link to update" in {
      val create = CreateLinkReq(name = "n", url = "javascript:x")
      LinkValidation.validateAll(Seq(create), Seq.empty).left.value.head shouldBe LinkUrlNotAllowed
      val update = UpdateLinkReq(id = java.util.UUID.randomUUID(), name = "n", url = "data:x")
      LinkValidation.validateAll(Seq.empty, Seq(update)).left.value.head shouldBe LinkUrlNotAllowed
    }
  }
}
