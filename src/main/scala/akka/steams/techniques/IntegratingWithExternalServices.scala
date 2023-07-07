package akka.steams.techniques

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.pattern.ask
import akka.util.Timeout

import java.util.Date
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object IntegratingWithExternalServices extends App {
  implicit val system = ActorSystem("integ-ext")
  implicit val materializer = ActorMaterializer()

  //import system.dispatcher // not recommended for mapAsyc -- will starve threads
  implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher")
  implicit val timeout = Timeout(3 seconds)

  def genericExternalService[A, B](element: A): Future[B] = ???

  //example: simplified PagerDuty
  case class PagerEvent(application: String, description: String, date: Date)

  val eventSource = Source(List(
    PagerEvent("akkaInfra", "infra broke", new Date),
    PagerEvent("fast data pipeline", "ill elements in pipeline", new Date),
    PagerEvent("akkaInfra", "service stopped responding", new Date),
    PagerEvent("super frontend", "button is red", new Date),
  ))

  /*object PagerService {
    private val engineers = List("Radu", "Niculian", "Mao")
    private val emails = Map(
      "Radu" -> "r@abc.com",
      "Niculian" -> "n@abc.com",
      "Mao" -> "m@abc.com",
    )

    def processEvent(pagerEvent: PagerEvent): Future[String] = Future {
      val engineerIndex = (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val email = emails(engineer)

      // page the engineer
      println(s"PAGING $engineer at $email: ${pagerEvent.description}")
      Thread.sleep(1000)
      email
    }
  }*/

  class PagerActor extends Actor with ActorLogging {

    private val engineers = List("Radu", "Niculian", "Mao")
    private val emails = Map(
      "Radu" -> "r@abc.com",
      "Niculian" -> "n@abc.com",
      "Mao" -> "m@abc.com",
    )

    def processEvent(pagerEvent: PagerEvent): String = {
      val engineerIndex = (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val email = emails(engineer)

      // page the engineer
      log.info(s"PAGING $engineer at $email: ${pagerEvent.description}")
      Thread.sleep(1000)
      email
    }

    override def receive: Receive = {
      case pagerEvent: PagerEvent => sender() ! processEvent(pagerEvent)
    }
  }

  val pagerActor = system.actorOf(Props[PagerActor], "pager-actor")

  val infraEvents = eventSource.filter(_.application == "akkaInfra")
  // mapAsync preserves order
  // mapAsyncUnordered doesn't preserve order
  //val pagedEngineerEmails = infraEvents.mapAsync(parallelism = 4)(event => PagerService.processEvent(event))
  val pagedEngineerEmails = infraEvents.mapAsync[String](parallelism = 2)(event => (pagerActor ? event).mapTo[String])
  val pagedEmailSink = Sink.foreach[String](s => println(s"Email sent to: $s"))

  pagedEngineerEmails.to(pagedEmailSink).run()

  // DO NOT CONFUSE mapAsync with async
}
