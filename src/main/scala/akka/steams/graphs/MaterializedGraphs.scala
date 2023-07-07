package akka.steams.graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, FlowShape, SinkShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object MaterializedGraphs extends App {
  implicit val system = ActorSystem("mat-graph")
  implicit val materializer = ActorMaterializer()

  val wordSource = Source(List("akka", "is", "awesome", "Radu", "and", "Mao", "are", "cool"))
  val printer = Sink.foreach[String](println)
  val counter = Sink.fold[Int, String](0)((i, _) => i + 1)

  /*
  composite component that acts like a sink which prints out all strings which are lowercase
  and also counts the strings that are short (< 5)
  */
  val complexSink = Sink.fromGraph(
    GraphDSL.create(printer, counter)((_, counterMat) => counterMat) { implicit builder =>
      (printerShape, counterShape) =>
        import GraphDSL.Implicits._
        val broadcast = builder.add(Broadcast[String](2))
        val filterLowercase = builder.add(Flow[String].filter(s => s == s.toLowerCase))
        val filterShort = builder.add(Flow[String].filter(_.length < 5))
        broadcast.out(0) ~> filterLowercase ~> printerShape
        broadcast.out(1) ~> filterShort ~> counterShape
        SinkShape(broadcast.in)
    }
  )

  /*val shortStringsCountFuture = wordSource.toMat(complexSink)(Keep.right).run()
  shortStringsCountFuture.onComplete {
    case Success(i) => println(s"There are $i short strings")
    case Failure(_) => println("Failed to count properly")
  }(system.dispatcher)*/

  /**
   * Exercise
   */
  def enhanceFlow[A, B](flow: Flow[A, B, _]): Flow[A, B, Future[Int]] = {
    val sink = Sink.fold[Int, B](0)((counter, _) => counter + 1)

    val enhancedFlow = GraphDSL.create(sink) {
      implicit builder =>
        sinkShape =>
          import GraphDSL.Implicits._
          val broadcast = builder.add(Broadcast[B](2))
          val originalFlowShape = builder.add(flow)
          originalFlowShape ~> broadcast ~> sinkShape
          FlowShape(originalFlowShape.in, broadcast.out(1))
    }
    Flow.fromGraph(enhancedFlow)
  }

  val upperCaseFlow = Flow[String].map(_.toUpperCase)

  val counterFuture = wordSource.viaMat(enhanceFlow(upperCaseFlow))(Keep.right).to(Sink.foreach(println)).run()
  counterFuture.onComplete {
    case Success(s) =>
      println(s"There have been $s numbers")
    case Failure(_) =>
      println(s"Failed to do anything")
  }(system.dispatcher)
}
