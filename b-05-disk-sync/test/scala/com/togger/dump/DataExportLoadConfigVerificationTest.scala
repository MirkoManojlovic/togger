package com.togger.dump

import com.togger.common.NonEmptyChainHelpers

import java.util.UUID

class DataExportLoadConfigVerificationTest extends TgWordSpecJvm with EitherValues with NonEmptyChainHelpers {

  val verification = new DataExportLoadConfigVerification

  private def existingDumpPath: os.Path = {
    val path = os.pwd / "target" / s"test-data-export-config-${UUID.randomUUID()}"
    os.makeDir.all(path)
    path
  }

  "DataExportLoadConfigVerification.verify" should {

    "return name and dump path when both are valid and the path exists" in {
      val name = "My dump config"
      val dumpPath = existingDumpPath.toString

      val result = verification.verify(name, dumpPath)

      result.value shouldBe (name, dumpPath)
    }

    "return error when the path is relative" in {
      val dumpPath = "relative/dump/path"

      val result = verification.verify("My dump config", dumpPath)

      result.left.value.asList should contain only InvalidAbsolutePath(dumpPath)
    }

    "return error when the path is relative (home)" in {
      val dumpPath = "~/Desktop"

      val result = verification.verify("My dump config", dumpPath)

      result.left.value.asList should contain only InvalidAbsolutePath(dumpPath)
    }

    "return error when the path is blank" in {
      val dumpPath = ""

      val result = verification.verify("My dump config", dumpPath)

      result.left.value.asList should contain only InvalidAbsolutePath(dumpPath)
    }

    "return error when the name is blank" in {
      val name = ""

      val result = verification.verify(name, existingDumpPath.toString)

      result.left.value.asList should contain only BlankName(name)
    }

    "return error when the name is whitespace only" in {
      val name = "   "

      val result = verification.verify(name, existingDumpPath.toString)

      result.left.value.asList should contain only BlankName(name)
    }

    "return error when the path is well-formed but does not exist" in {
      val dumpPath = (os.home / "Desktop" / s"nonexistent-dump-config-${UUID.randomUUID()}").toString

      val result = verification.verify("My dump config", dumpPath)

      result.left.value.asList should contain only PathDoesNotExist(dumpPath)
    }

    "accumulate errors when both the name and the path are invalid" in {
      val name = ""
      val dumpPath = "relative/dump/path"

      val result = verification.verify(name, dumpPath)

      result.left.value.asList should contain theSameElementsAs List(BlankName(name), InvalidAbsolutePath(dumpPath))
    }

  }

}
