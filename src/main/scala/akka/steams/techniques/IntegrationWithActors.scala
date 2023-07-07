package akka.steams.techniques

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

object IntegrationWithActors extends App {
  implicit val system = ActorSystem("integ-actor")
  implicit val materializer = ActorMaterializer()

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case s: String =>
        log.info(s"[STR] $s")
        sender() ! s"$s$s"
      case i: Int =>
        log.info(s"[INT]$i")
        sender() ! (2 * i)
    }
  }

  val simpleActor = system.actorOf(Props[SimpleActor], "simple-actor")

  val numberSource = Source(1 to 10)

  /*
  Actor as a flow
   */
  implicit val timeout = Timeout(2 seconds)
  val actorFlow = Flow[Int].ask[Int](parallelism = 4)(simpleActor)

  //numberSource.via(actorFlow).to(Sink.ignore).run()
  // numberSource.ask[Int](4)(simpleActor).to(Sink.ignore) //equivalent

  /*
  Actor as a source
   */

  val actorPoweredSource = Source.actorRef[Int](bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead)
  val streamActor = actorPoweredSource.to(Sink.foreach[Int](println)).run()

  /*val sch = system.scheduler.schedule(2 seconds, 2 seconds) {
    streamActor ! Random.nextInt(10)
  }(system.dispatcher)

  // terminating the stream
  system.scheduler.scheduleOnce(16 seconds) {
    streamActor ! akka.actor.Status.Success("complete")
    sch.cancel()
  }(system.dispatcher)*/

  /*
  Actor as a sink
  - an init message
  - an ack message to confirm the reception
  - a complete message
  - a function to generate a message in case the stream throws an exception
   */
  case object StreamInit

  case object StreamAck

  case object StreamComplete

  case class StreamFail(ex: Throwable)

  class DestinationActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case StreamInit =>
        log.info("[STREAM INIT]")
        sender() ! StreamAck
      case StreamComplete =>
        log.info("[STREAM COMPLETE]")
        context.stop(self)
      case StreamFail(er) => log.error(s"[FAIL]: ${er.getMessage}")
      case message =>
        log.info(message.toString)
        sender() ! StreamAck
    }
  }

  val destActor = system.actorOf(Props[DestinationActor], "destination-actor")

  val actorSink = Sink.actorRefWithAck[Int](destActor,
    onInitMessage = StreamInit,
    onCompleteMessage = StreamComplete,
    ackMessage = StreamAck,
    onFailureMessage = StreamFail)

  Source(1 to 10).to(actorSink).run()


}
