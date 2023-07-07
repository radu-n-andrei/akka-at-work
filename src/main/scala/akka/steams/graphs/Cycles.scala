package akka.steams.graphs

import akka.actor.ActorSystem
import akka.stream.javadsl.MergePreferred
import akka.stream.{ActorMaterializer, ClosedShape, FanInShape, FanInShape2, OverflowStrategy, SinkShape, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Merge, RunnableGraph, Source, ZipWith}

object Cycles extends App {
  implicit val system = ActorSystem("cycles")
  implicit val materializer = ActorMaterializer()

  val accelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val incShape = builder.add(Flow[Int].map(i => {
      println(s"Accelerating $i")
      i + 1
    }
    ))
    sourceShape ~> mergeShape ~> incShape
    mergeShape <~ incShape
    ClosedShape
  }

  val g = RunnableGraph.fromGraph(accelerator) // graph cycle deadlock - backpressure
  //g.run()

  /*
  Solution 1: MergePreferred
   */

  val actualAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(MergePreferred.create[Int](1))
    val incShape = builder.add(Flow[Int].map(i => {
      println(s"Accelerating $i")
      i + 1
    }
    ))
    sourceShape ~> mergeShape ~> incShape
    incShape ~> mergeShape.preferred
    ClosedShape
  }

  val g2 = RunnableGraph.fromGraph(actualAccelerator)
  //g2.run()

  /*
  Solution 2: Buffers
   */
  val bufferedAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val incShape = builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map(i => {
      println(s"Accelerating $i")
      Thread.sleep(100)
      i
    }
    ))
    sourceShape ~> mergeShape ~> incShape
    incShape ~> mergeShape
    ClosedShape
  }

  val g3 = RunnableGraph.fromGraph(bufferedAccelerator)
  //g3.run()

  /**
   * Ex. Fan in shape takes 2 inputs fed with one number, output will emit infinite fibo sequence
   */

  val soloSource1 = Source.single(1)

  val fiboShape = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val zipWithShape = builder.add(ZipWith[Int, Int, Int]((a, b) => a + b))
    val printAndFwd = builder.add(Flow[Int].map(x => {
      println(s"$x")
      Thread.sleep(200)
      x
    }))
    val startBroadcast = builder.add(Broadcast[Int](3))
    val resultBroadcast = builder.add(Broadcast[Int](2))
    val mergeA = builder.add(MergePreferred.create[Int](2))
    val mergeB = builder.add(Merge[Int](2))
    startBroadcast.out(0) ~> mergeA.in(0)
    startBroadcast.out(1) ~> mergeA.in(1)
    startBroadcast.out(2) ~> mergeB.in(0)
    mergeA.out ~> printAndFwd ~> zipWithShape.in0
    mergeB.out ~> zipWithShape.in1
    zipWithShape.out ~> resultBroadcast.in
    resultBroadcast.out(0) ~> mergeA.preferred
    resultBroadcast.out(1) ~> mergeB.in(1)
    SinkShape(startBroadcast.in)
  }

  soloSource1.to(fiboShape).run()
}
