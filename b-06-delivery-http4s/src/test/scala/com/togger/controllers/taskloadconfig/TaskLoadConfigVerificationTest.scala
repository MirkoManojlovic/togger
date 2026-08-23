package com.togger.controllers.taskloadconfig

import com.togger.TgWordSpecJvm
import org.scalatest.EitherValues

class TaskLoadConfigVerificationTest extends TgWordSpecJvm with EitherValues {

  val verification = new TaskLoadConfigVerification

  "TaskLoadConfigVerification.verify" should {

    "return name and dump path when both are valid" in {
      val name = "My load config"
      val dumpPath = "/tmp/togger/dumps"

      val result = verification.verify(name, dumpPath)

      result.value shouldBe (name, dumpPath)
    }

    "return error when the path is relative" in {
      val dumpPath = "relative/dump/path"

      val result = verification.verify("My load config", dumpPath)

      result.left.value shouldBe InvalidPath(dumpPath)
    }

    "return error when the path is relative (home)" in {
      val dumpPath = "~/relative/dump/path"

      val result = verification.verify("My load config", dumpPath)

      result.left.value shouldBe InvalidPath(dumpPath)
    }

    "return error when the path is blank" in {
      val dumpPath = ""

      val result = verification.verify("My load config", dumpPath)

      result.left.value shouldBe InvalidPath(dumpPath)
    }

    "return error when the name is blank" in {
      val name = ""

      val result = verification.verify(name, "/tmp/togger/dumps")

      result.left.value shouldBe BlankName(name)
    }

    "return error when the name is whitespace only" in {
      val name = "   "

      val result = verification.verify(name, "/tmp/togger/dumps")

      result.left.value shouldBe BlankName(name)
    }

  }

}
