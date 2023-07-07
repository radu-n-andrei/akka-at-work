package akka.steams.graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, FanOutShape, FanOutShape2}
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}
import scala.concurrent.duration._
import scala.language.postfixOps

object GraphBasics extends App {

  implicit val system = ActorSystem("graph-101")
  implicit val materializer = ActorMaterializer()

  val input = Source(1 to 1000)
  val incrementer = Flow[Int].map(_ + 1)
  val multiplier = Flow[Int].map(_ * 10)
  // execute both flows in parallel and return a tuple
  val output = Sink.foreach[(Int, Int)](println)

  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[Int](2)) // fan out operator
      val zip = builder.add(Zip[Int, Int]) // fan in operator
      // put them all together ~> = feed in
      input ~> broadcast
      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier ~> zip.in1
      zip.out ~> output
      ClosedShape // return the shape
    }
  )

  //graph.run() // run and materialize
  /**
   * exercises.
   * 1. feed input into 2 sinks
   */

  val output1 = Sink.foreach[Int](i => println(s"S1: $i"))
  val output2 = Sink.foreach[Int](i => println(s"S2: $i"))

  val graphEx1 = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[Int](2))
      input ~> broadcast
      broadcast.out(0) ~> output1
      broadcast.out(1) ~> output2
      ClosedShape
    }
  )

  //graphEx1.run()

  /**
   * 2. 2 sources -> merge -> balance -> 2 sinks
   */
  val source1 = Source[Int](1 to 10)
  val source2 = Source[Int](11 to 20).throttle(2, 1 second)

  val foldSink = Sink.fold[Int, Int](0)((count, _) => {
    println(s"Count1 = $count")
    count + 1
  })
  val foldSink2 = Sink.fold[Int, Int](0)((count, _) => {
    println(s"Count2 = $count")
    count + 1
  })
  val ex2Graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val merge = builder.add(Merge[Int](2))
      val balance = builder.add(Balance[Int](2))
      source1 ~> merge ~> balance ~> foldSink
      source2 ~> merge
      balance ~> foldSink2
      ClosedShape
    }
  )

  ex2Graph.run()

}
