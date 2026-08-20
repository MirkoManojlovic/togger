package com.togger.controllers.taskdumpconfig

import com.togger.TgWordSpecJvm
import org.scalatest.EitherValues

class TaskDumpConfigVerificationTest extends TgWordSpecJvm with EitherValues {

  val verification = new TaskDumpConfigVerification

  "TaskDumpConfigVerification.verify" should {

    "return dump path when it is a valid absolute path" in {
      val dumpPath = "/tmp/togger/dumps"

      val result = verification.verify(dumpPath)

      result.value shouldBe dumpPath
    }

    "return error when the path is relative" in {
      val dumpPath = "relative/dump/path"

      val result = verification.verify(dumpPath)

      result.left.value shouldBe InvalidPath(dumpPath)
    }

    "return error when the path is relative (home)" in {
      val dumpPath = "~/relative/dump/path"

      val result = verification.verify(dumpPath)

      result.left.value shouldBe InvalidPath(dumpPath)
    }

    "return error when the path is blank" in {
      val dumpPath = ""

      val result = verification.verify(dumpPath)

      result.left.value shouldBe InvalidPath(dumpPath)
    }

  }

}
