package akka.steams.techniques

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import scala.concurrent.duration._
import scala.language.postfixOps

import java.util.Date

object AdvancedBackpressure extends App {
  implicit val system = ActorSystem("adv-back")
  implicit val materializer = ActorMaterializer()

  val controlledFlow = Flow[Int].map(_ * 2).buffer(size = 10, overflowStrategy = OverflowStrategy.dropHead)

  case class PagerEvent(desc: String, date: Date, nInstances: Int = 1)

  case class Notification(email: String, pagerEvent: PagerEvent)

  val events = List(PagerEvent("desc1", new Date),
    PagerEvent("desc2", new Date),
    PagerEvent("desc3", new Date),
    PagerEvent("desc4", new Date)
  )

  val eventSource = Source(events)

  // simulates a fast service for on call emails
  val onCallEngineer = "radu@home.com"

  def sendEmail(notification: Notification) =
    println(s"${notification.email} will receive an event: ${notification.pagerEvent.desc}")

  def sendEmailSlow(notification: Notification) = {
    Thread.sleep(1000)
    println(s"${notification.email} will receive an event: ${notification.pagerEvent.desc}")
  }

  val notificationSink = Flow[PagerEvent].map(event => Notification(onCallEngineer, event)).to(Sink.foreach[Notification](sendEmail))

  /**
   * 1. standard
   */
  eventSource.to(notificationSink)//.run()

  /**
   * alternative to backpressure - it will aggregate upstream items until the downstream is ready to accept a new value
   * !!!! async needs to be used otherwise it will run as usual, since it will all be on the same actor
   */

  val aggregateNotificationFlow = Flow[PagerEvent].conflate((event1, event2) => {
    val nInstances = event1.nInstances + event2.nInstances
    PagerEvent(s"You have $nInstances events that require your attention", new Date, nInstances)
  }).map(event => Notification(onCallEngineer, event))
  eventSource.via(aggregateNotificationFlow).async.to(Sink.foreach(sendEmailSlow))//.run()

  /**
   * Slow producer: extrapolate/expand
   */
  val slowCounter = Source(LazyList.from(1)).throttle(1, 1 second)
  val fastSink = Sink.foreach[Int](println)
  // flows that fill in the gaps
  // !! extrapolate - only when there is unmet demand (i.e. slow source, fast consumers); expand - always
  val extrapolator = Flow[Int].extrapolate(element => Iterator.from(element))
  val repeater = Flow[Int].extrapolate(element => Iterator.continually(element))
  val expander = Flow[Int].expand(element => Iterator.from(element))
  slowCounter.via(expander).to(fastSink).run
}
