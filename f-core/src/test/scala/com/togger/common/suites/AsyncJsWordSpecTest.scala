package com.togger.common.suites

import com.raquo.laminar.api.L._
import org.scalatest.matchers.should.Matchers
import scribe.Logger
import scribe.format.Formatter

/*
  This is a test for the AsyncJsWordSpec
 */
class AsyncJsWordSpecTest extends AsyncJsWordSpec with Matchers with OwnerForTestingTrait {

  "AsyncJsWordSpec" should {
    "should assert events properly with no delay" in { owner =>
      // TODO: move this logger to a common TestSuite
      Logger.root
        .clearHandlers()
        .withHandler(formatter = Formatter.compact)
        .replace()

      val events = Var("")
      val eventsStream = events.signal.changes

      val testEvents = TestEvents(eventsStream, owner)

      val firstEvent = "First event"
      val secondEvent = "Second event"

      runTasksAsyncFuture[String](
        testEvents,
        DelayedTask(
          delay = 0,
          testEventsNew => {
            testEventsNew.printEvents()
            testEventsNew.events shouldBe empty

            events.set(firstEvent)
            events.set(secondEvent)

            testEventsNew.printEvents()
            testEventsNew.events should have size 2
            testEventsNew.events shouldBe Seq(firstEvent, secondEvent)
          }
        )
      )
    }

    "should assert events properly with delay" in { owner =>
      val events = Var("")
      val eventsStream = events.signal.changes.delay(100)

      val testEvents = TestEvents(eventsStream, owner)

      val firstEvent = "First event"
      val secondEvent = "Second event"

      runTasksAsyncFuture[String](
        testEvents,
        DelayedTask(
          delay = 0,
          testEventsNew => {
            events.set(firstEvent)
            events.set(secondEvent)

            testEventsNew.printEvents()
            testEventsNew.events shouldBe empty
          }
        ),
        DelayedTask(
          delay = 200,
          testEventsNew => {
            testEventsNew.printEvents()
            testEventsNew.events should have size 2
            testEventsNew.events shouldBe Seq(firstEvent, secondEvent)
          }
        )
      )
    }

  }

}
