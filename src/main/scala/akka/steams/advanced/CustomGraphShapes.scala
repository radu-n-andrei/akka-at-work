package akka.steams.advanced

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape, Graph, Inlet, Outlet, Shape}

import scala.concurrent.duration._
import scala.language.postfixOps

object CustomGraphShapes extends App {
  implicit val system = ActorSystem("custom-shapes")
  implicit val materializer = ActorMaterializer()

  /**
   * 2x3 Balance
   */
  case class Balance2x3(in0: Inlet[Int],
                        in1: Inlet[Int],
                        out0: Outlet[Int],
                        out1: Outlet[Int],
                        out2: Outlet[Int]) extends Shape {
    //Inlet[T], Outlet[T] - ports
    override val inlets: Seq[Inlet[_]] = List(in0, in1)

    override val outlets: Seq[Outlet[_]] = List(out0, out1, out2)

    override def deepCopy(): Shape = Balance2x3(in0.carbonCopy(),
      in1.carbonCopy(), out0.carbonCopy(),
      out1.carbonCopy(), out2.carbonCopy())
  }

  val balance2x3Impl = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val mergeShape = builder.add(Merge[Int](2))
    val balanceShape = builder.add(Balance[Int](3))
    mergeShape ~> balanceShape
    Balance2x3(mergeShape.in(0), mergeShape.in(1), balanceShape.out(0), balanceShape.out(1), balanceShape.out(2))
  }

  val balance2x3Graph = RunnableGraph.fromGraph(
    GraphDSL.create() {
      implicit builder =>
        import GraphDSL.Implicits._
        val slowSource = Source(LazyList.from(1)).throttle(1, 1 second)
        val fastSource = Source(LazyList.from(100)).throttle(2, 1 second)

        def createSink(index: Int) = Sink.fold[Int, Int](0)((acc, el) => {
          println(s"Sink $index got element $el. Total elements through sink: ${acc + 1}")
          acc + 1
        })

        val sinks = (1 to 3).map(createSink)

        val balance2x3 = builder.add(balance2x3Impl)
        slowSource ~> balance2x3.in0
        fastSource ~> balance2x3.in1
        balance2x3.out0 ~> sinks(0)
        balance2x3.out1 ~> sinks(1)
        balance2x3.out2 ~> sinks(2)

        ClosedShape
    }
  )

  //balance2x3Graph.run()

  /**
   * Exercise: MxN balance, generic
   */

  case class BalanceMxN[T](override val inlets: List[Inlet[T]], override val outlets: List[Outlet[T]]) extends Shape {
    override def deepCopy(): Shape = BalanceMxN(inlets.map(_.carbonCopy()), outlets.map(_.carbonCopy()))
  }

  object BalanceMxN {
    def impl[T](inputs: Int, outputs: Int): Graph[BalanceMxN[T], NotUsed] = GraphDSL.create() {
      implicit builder =>
        import GraphDSL.Implicits._
        val mergeShape = builder.add(Merge[T](inputs))
        val balanceShape = builder.add(Balance[T](outputs))
        mergeShape ~> balanceShape
        BalanceMxN(mergeShape.inlets.toList, balanceShape.outlets.toList)
    }
  }

  val runnableMxN = RunnableGraph.fromGraph(
    GraphDSL.create() {
      implicit builder =>
        import GraphDSL.Implicits._
        val multiBalance = builder.add(BalanceMxN.impl[Int](2, 3))
        val slowSource = Source(LazyList.from(1)).throttle(1, 1 second)
        val fastSource = Source(LazyList.from(100)).throttle(2, 1 second)

        def createSink(index: Int) = Sink.fold[Int, Int](0)((acc, el) => {
          println(s"Sink $index got element $el. Total elements through sink: ${acc + 1}")
          acc + 1
        })

        val sinks = (1 to 3).map(createSink)
        slowSource ~> multiBalance.inlets(0)
        fastSource ~> multiBalance.inlets(1)
        multiBalance.outlets(0) ~> sinks(0)
        multiBalance.outlets(1) ~> sinks(1)
        multiBalance.outlets(2) ~> sinks(1)

        ClosedShape
    }
  )

  runnableMxN.run()


}
