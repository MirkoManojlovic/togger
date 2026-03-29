package com.togger.common.suites

import com.togger.suites.TestNameHelper
import com.raquo.airstream.ownership.Owner
import com.togger.logging.TestLoggingJs
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.FixtureAsyncWordSpec
import org.scalatest.{Assertion, Checkpoints, FutureOutcome}

import scala.annotation.nowarn
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import scala.util.Try

abstract class AsyncJsWordSpec
    extends FixtureAsyncWordSpec
    with Matchers
    with Checkpoints
    with TestLoggingJs
    with TestNameHelper {

  implicit override def executionContext: ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  type FixtureParam = Owner

  override def withFixture(test: OneArgAsyncTest): FutureOutcome = {
    val testOwner = new OwnerForTesting()
    complete {
      withFixture(test.toNoArgAsyncTest(testOwner))
    } lastly {
      logger.info(s"🔪 Killing subscriptions after test")
      testOwner.killSubscriptions()
    }
  }

  case class DelayedTask[A](delay: Int, task: (TestEvents[A]) => Assertion)

  // ignores cp(assertion) pure expression vs statement warning
  @nowarn
  def runTasksAsyncFuture[A](TestEvents: TestEvents[A], taskParams: DelayedTask[A]*): Future[Assertion] = {
    taskParams
      .foldLeft(emptyAccumulatorFuture)((accumulator, taskParam) =>
        accumulator.flatMap(assertions =>
          inAsyncContextFuture(runTheTask(TestEvents, taskParam, assertions), taskParam.delay)
        )(executionContext)
      )
      .map { assertions =>
        TestEvents.close()

        val cp = new Checkpoint
        assertions.foreach(assertion => cp(assertion))
        cp.reportAll()
        assertions.head

      }(executionContext)

  }

  def inAsyncContextFuture[A](code: => A, delay: Int): Future[A] = {
    val promise = Promise[A]()
    js.timers.setTimeout(delay.toDouble) {
      promise.complete(Try(code))
    }
    promise.future.map(identity)(executionContext)
  }

  def emptyAccumulatorFuture: Future[Seq[Assertion]] = Future(Seq.empty)

  def runTheTask[A](testEvents: TestEvents[A], taskParam: DelayedTask[A], assertions: Seq[Assertion]): Seq[Assertion] =
    taskParam.task(testEvents) +: assertions

}
