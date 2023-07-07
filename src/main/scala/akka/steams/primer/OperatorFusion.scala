package akka.steams.primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.ExecutionContext

object OperatorFusion extends App {

  implicit val system = ActorSystem("fusion")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher

  val simpleSource = Source(1 to 1000)
  val simpleFlow = Flow[Int].map(_ + 1)
  val simpleFlow2 = Flow[Int].map(_ * 10)
  val simpleSink = Sink.foreach[Int](println)

  // runs on the same actor - OPERATOR FUSION
  //simpleSource.via(simpleFlow).via(simpleFlow2).to(simpleSink).run()

  // complex operators
  val complexFlow = Flow[Int].map { x =>
    Thread.sleep(1000)
    x + 1
  }
  val complexFlow2 = Flow[Int].map { x =>
    Thread.sleep(1000)
    x * 10
  }

  //simpleSource.via(complexFlow).via(complexFlow2).to(simpleSink).run()

  // async boundary
  /*simpleSource.via(complexFlow).async // runs on one actor
    .via(complexFlow2).async.to(simpleSink).run*/

  // ordering guarantees
  Source(1 to 3).map(el => {
    println(s"Flow A: $el")
    el
  }).async.map(el => {
    println(s"Flow B: $el")
    el
  }).async.map(el => {
    println(s"Flow C: $el")
    el
  }).async.runWith(Sink.ignore)
}
