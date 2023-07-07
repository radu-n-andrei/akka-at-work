package akka.steams.graphs

import akka.actor.ActorSystem
import akka.stream.FanOutShape.{Name, Ports}
import akka.stream.impl.{FanOut, FanoutProcessorImpl}
import akka.stream.{ActorMaterializer, ClosedShape, FanOutShape, FanOutShape2, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, ZipWith}

object OtherOpenGraphs extends App {
  implicit val system = ActorSystem("other-g")
  implicit val materializer = ActorMaterializer()

  /*
  Example: max 3 operator
  3 inputs of Int, when we have inputs for all 3 push out max
   */

  val max3StaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val max1 = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))
    val max2 = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))
    max1.out ~> max2.in0
    // same for fan out; Uniform because all inoputs are the same
    UniformFanInShape(max2.out, max1.in0, max1.in1, max2.in1)
  }

  val source1 = Source(1 to 10)
  val source2 = Source((1 to 10).map(_ => 5))
  val source3 = Source((1 to 10).reverse)
  val maxSink = Sink.foreach[Int](i => println(s"Max is $i"))

  val max3Runnable = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val max3 = builder.add(max3StaticGraph)
      source1 ~> max3.in(0)
      source2 ~> max3.in(1)
      source3 ~> max3.in(2)
      max3 ~> maxSink
      ClosedShape
    }
  )

  //max3Runnable.run()

  /*
  Non uniform fan out shape
  Ex. bank transactions => amt > 10k = suspicious
  input transactions -> 2 outputs: (1)same transaction (2) transaction ids for suspicious
   */

  case class Transaction(id: String, amount: Int)

  val transactionSource = Source(List(Transaction("real1", 5000),
    Transaction("real2", 8000), Transaction("sus1", 10000),
    Transaction("sus2", 11000), Transaction("real3", 3000)))

  val validSink = Sink.foreach[Transaction](t => println(s"Processing transaction ${t.id} for ${t.amount}"))
  val invalidSink = Sink.foreach[String](s => println(s"Red flag for transaction $s"))

  val susGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val broadcast = builder.add(Broadcast[Transaction](2))
    val susFilter = builder.add(Flow[Transaction].filter(_.amount >= 10000))
    val validFilter = builder.add(Flow[Transaction].filter(_.amount < 10000))
    val idExtractor = builder.add(Flow[Transaction].map[String](_.id))
    broadcast.out(0) ~> susFilter ~> idExtractor
    broadcast.out(1) ~> validFilter
    new FanOutShape2(broadcast.in, validFilter.out, idExtractor.out)
  }

  val runnableGraph2 = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val susShape = builder.add(susGraph)
      transactionSource ~> susShape.in
      susShape.out0 ~> validSink
      susShape.out1 ~> invalidSink
      ClosedShape
    }
  )

  runnableGraph2.run()


}
