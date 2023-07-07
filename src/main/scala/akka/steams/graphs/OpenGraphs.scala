package akka.steams.graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, FlowShape, SinkShape, SourceShape}
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}

object OpenGraphs extends App {
  implicit val system = ActorSystem("open-g")
  implicit val materializer = ActorMaterializer()

  /*
  a composite source that concats 2 sources; it emits all the elements from the 1st source then the 2nd
   */
  val firstSource = Source(1 to 10)
  val secondSource = Source(11 to 100)

  val concatSourceGraph = Source.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val concat = builder.add(Concat[Int](2))
      firstSource ~> concat
      secondSource ~> concat
      SourceShape(concat.out)
    }
  )

  //concatSourceGraph.to(Sink.foreach(println)).run()
  /*
  feed a source into 2 sinks
   */
  val sink1 = Sink.foreach[Int](i => println(s"S1 -> $i"))
  val sink2 = Sink.foreach[Int](i => println(s"S2 -> $i"))

  val complexSinkGraph = Sink.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[Int](2))
      broadcast ~> sink1
      broadcast ~> sink2
      SinkShape(broadcast.in)
    }
  )

  //firstSource.to(complexSinkGraph).run()

  /**
   * challenge - complex flow
   * flow composed of 2 other flows
   * - one that adds 1 to a number
   * - one that multiplies by 10
   */

  val incFlow = Flow[Int].map(_ + 1)
  val multFlow = Flow[Int].map(_ * 10)
  val dumbFlow = Flow[(Int, Int)].map[Int] {
    case (a, b) => a * b
  }
  val complexFlow = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val incShape = builder.add(incFlow)
      val multShape = builder.add(multFlow)
      incShape ~> multShape
      FlowShape.of(incShape.in, multShape.out)
    }
  )

  //firstSource.via(complexFlow).to(Sink.foreach[Int](println)).run()

  /**
   * creat ea flow from a sink and a source?
   */

  def fromSinkAndSource[A, B](sink: Sink[A, _], source: Source[B, _]): Flow[A, B, _] = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val sinkShape = builder.add(sink)
      val sourceShape = builder.add(source)
      FlowShape(sinkShape.in, sourceShape.out)
    }
  )

  // Coupled/ un coupled
  val f = Flow.fromSinkAndSourceCoupled(Sink.foreach(println), Source(120 to 130))

  firstSource.via(f).to(sink1).run()
}
