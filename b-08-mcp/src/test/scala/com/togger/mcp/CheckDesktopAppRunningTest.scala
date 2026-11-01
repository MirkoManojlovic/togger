package com.togger.mcp

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.DurationInt
import cats.effect.{Deferred, IO}
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.Checkpoints
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class CheckDesktopAppRunningTest extends AsyncWordSpec with AsyncIOSpec with Matchers with Checkpoints {

  "CheckDesktopAppRunning" should {

    "complete the stop signal once the desktop app is no longer running" in {
      val checks = AtomicInteger(0)

      for {
        stopSignal <- Deferred[IO, Unit]
        _ <- new CheckDesktopAppRunning(10.millis).run(() => checks.incrementAndGet() < 3, stopSignal)
        stopped <- stopSignal.tryGet
      } yield {
        val cp = new Checkpoint
        cp(checks.get() shouldBe 3)
        stopped shouldBe Some(())
      }
    }

    "keep polling while the desktop app is running, until cancelled" in {
      val checks = AtomicInteger(0)

      for {
        stopSignal <- Deferred[IO, Unit]
        fiber <- new CheckDesktopAppRunning(10.millis).run(() => checks.incrementAndGet() > 0, stopSignal).start
        _ <- IO.sleep(200.millis)
        checksWhileRunning = checks.get()
        _ <- fiber.cancel
        checksAfterCancel = checks.get()
        _ <- IO.sleep(100.millis)
        stopped <- stopSignal.tryGet
      } yield {
        val cp = new Checkpoint
        cp(checksWhileRunning should be > 1)
        cp(checks.get() shouldBe checksAfterCancel)
        stopped shouldBe None
      }
    }
  }
}
