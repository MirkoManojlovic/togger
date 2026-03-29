package com.togger.common.suites

import com.togger.logging.TgLogging
import com.raquo.airstream.ownership.Owner
import com.raquo.laminar.api.L._

import scala.collection.mutable

case class TestEvents[A](streamOfEvents: Observable[A], owner: Owner) extends AutoCloseable with TgLogging {
  private val internalEvents = mutable.Buffer[A]()

  def addEvent(event: A): Unit = {
    logger.info(s"🥁 New event in TestEvents: $event")
    internalEvents += event
    logger.info(s"Events: $events")
  }

  def nTh(i: Int): A = internalEvents(i)

  def events: Seq[A] = internalEvents.toSeq

  private val subscription = streamOfEvents.foreach(addEvent)(owner)

  override def close(): Unit = {
    logger.info(s"🔪 Killing subscriptions in TestEvents")
    subscription.kill()
  }

  def printEvents(): Unit = {
    logger.info(s"📝 Events so far")
    events.zipWithIndex
      .foreach { case (event, eventNumber) => logger.info(s"$eventNumber -- ${event.toString}") }
  }

}
