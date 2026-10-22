package com.togger.dump

import com.togger.common.NonEmptyChainHelpers

import java.util.UUID

class DataImportConfigVerificationTest extends TgWordSpecJvm with EitherValues with NonEmptyChainHelpers {

  val verification = new DataImportConfigVerification

  private def existingDumpPath: os.Path = {
    val path = os.pwd / "target" / s"test-data-export-config-${UUID.randomUUID()}"
    os.makeDir.all(path)
    path
  }

  "DataImportConfigVerification.verify" should {

    "return name and dump path when both are valid and the path exists" in {
      val name = "My dump config"
      val importPath = existingDumpPath.toString

      val result = verification.verify(name, importPath)

      result.value shouldBe (name, importPath)
    }

    "return error when the path is relative" in {
      val importPath = "relative/dump/path"

      val result = verification.verify("My dump config", importPath)

      result.left.value.asList should contain only InvalidAbsolutePath(importPath)
    }

    "return error when the path is relative (home)" in {
      val importPath = "~/Desktop"

      val result = verification.verify("My dump config", importPath)

      result.left.value.asList should contain only InvalidAbsolutePath(importPath)
    }

    "return error when the path is blank" in {
      val importPath = ""

      val result = verification.verify("My dump config", importPath)

      result.left.value.asList should contain only InvalidAbsolutePath(importPath)
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
      val importPath = (os.home / "Desktop" / s"nonexistent-dump-config-${UUID.randomUUID()}").toString

      val result = verification.verify("My dump config", importPath)

      result.left.value.asList should contain only PathDoesNotExist(importPath)
    }

    "accumulate errors when both the name and the path are invalid" in {
      val name = ""
      val importPath = "relative/dump/path"

      val result = verification.verify(name, importPath)

      result.left.value.asList should contain theSameElementsAs List(BlankName(name), InvalidAbsolutePath(importPath))
    }

  }

}
