package com.togger.mcp

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration.DurationInt

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CheckDesktopAppRunningTest extends AnyWordSpec with Matchers:

  "CheckDesktopAppRunning" should {

    "call onStopped once when the desktop app is no longer running" in {
      val checks = AtomicInteger(0)
      val stopped = AtomicInteger(0)
      val watcher = CheckDesktopAppRunning(
        isRunning = () => checks.incrementAndGet() < 3,
        checkInterval = 10.millis,
        onStopped = () => { stopped.incrementAndGet(); () }
      ).start()

      watcher.join(5000)

      checks.get() shouldBe 3
      stopped.get() shouldBe 1
    }

    "keep running while the desktop app is running" in {
      val stopped = AtomicInteger(0)
      val watcher = CheckDesktopAppRunning(
        isRunning = () => true,
        checkInterval = 10.millis,
        onStopped = () => { stopped.incrementAndGet(); () }
      ).start()

      Thread.sleep(200)

      watcher.isAlive shouldBe true
      stopped.get() shouldBe 0
    }

    "recognise the desktop app in ps output" in {
      val running = "user 123 0.0 /Applications/Togger.app/Contents/MacOS/app"
      CheckDesktopAppRunning.containsDesktopApp(running) shouldBe true
      CheckDesktopAppRunning.containsDesktopApp("user 123 0.0 /usr/bin/java -jar other.jar") shouldBe false
    }
  }
