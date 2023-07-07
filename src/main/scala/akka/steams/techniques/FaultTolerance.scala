package akka.steams.techniques

import akka.actor.ActorSystem
import akka.stream.Supervision.{Restart, Resume, Stop}
import akka.stream.{ActorAttributes, ActorMaterializer}
import akka.stream.scaladsl.{RestartSource, Sink, Source}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

object FaultTolerance extends App {
  implicit val system = ActorSystem("fault-tol")
  implicit val materializer = ActorMaterializer()

  // 1. logging
  // TODO says faultySource but the glitch is in the map, making this a faultyFlow?
  //  No: map is in FlowOps and will call via which is overriden by Source to return a Source (which is a FlowOps as well)
  val faultySource = Source(1 to 10).map(el => if (el == 6) throw new RuntimeException() else el)
  faultySource.log("trackingElements").to(Sink.ignore) //.run()

  // 2. gracefully terminating a stream
  faultySource.recover {
    case _: RuntimeException => Int.MinValue
  }.log("gracefulSource").to(Sink.ignore) //.run()

  // 3. recover with another stream
  faultySource.recoverWithRetries(3, {
    case _: RuntimeException => Source(90 to 99)
  }).log("recover").to(Sink.ignore) //.run()

  // 4. backoff supervision
  val restartSource = RestartSource.onFailuresWithBackoff(
    minBackoff = 1 second,
    maxBackoff = 30 seconds,
    randomFactor = 0.2
  )(() => {
    val randomNumber = new Random().nextInt(20)
    Source(1 to 10).map(element => if (element == randomNumber) throw new RuntimeException() else element)
  })

  restartSource.log("restartBackoff").to(Sink.ignore) //.run()

  // 5. supervision strategy
  val numbers = Source(1 to 20).map(n => if (n == 13) throw new RuntimeException() else n).fold[Int](0)(_ + _).log("supervision")
  val supervisedNumbers = numbers.withAttributes(ActorAttributes.supervisionStrategy {
    // Resume = skips, Stop = stop, Restart = resume + clear the internal state
    case _: RuntimeException => Resume
    case _ => Stop
  })
  supervisedNumbers.to(Sink.ignore) //.run()

  // 5.2 - numbers with restart - the stream element gets restarted and loses the internal state, in this case, the flow restarts the count from 14
  val numbers2 = Source(1 to 20).fold[Int](0)((acc, el) => {
    if (el == 13) throw new RuntimeException()
    else acc + el
  }).log("supervision")

  numbers2.withAttributes(ActorAttributes.supervisionStrategy {
    case _: RuntimeException => Restart
    case _ => Stop
  }).to(Sink.ignore).run()
}
