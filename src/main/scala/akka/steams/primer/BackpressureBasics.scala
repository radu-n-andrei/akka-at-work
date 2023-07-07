package akka.steams.primer

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import scala.concurrent.duration._
import scala.language.postfixOps

object BackpressureBasics extends App {
  implicit val actorSys: ActorSystem = ActorSystem("backpress")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { i =>
    Thread.sleep(1000)
    println(s"Sink $i")
  }

  fastSource.to(slowSink).run() // not backpressure because it's a single actor instance

  fastSource.async.to(slowSink).run() // backpressure because they act on separate actors

  val simpleFlow = Flow[Int].map{ i =>
    println(s"Flowing $i")
    i * 2
  }

  fastSource.async.via(simpleFlow).async.to(slowSink).run() // will sendover backpressure all the way to source

  /*
  reaction to backpressure:
  1. try to slow down if possible
  2. buffer elements until there's more demand
  3. drop down elements from the buffer if it overflows
  4. tear down/kill the stream (failure)
   */
  val bufferedFlow = simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.dropHead)

  fastSource.async.via(bufferedFlow).async.to(slowSink).run()

  // control speed at the source
  fastSource.throttle(2, 1 second).runWith(slowSink)
}
