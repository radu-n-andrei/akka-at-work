package akka.steams.graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, BidiShape, ClosedShape}
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}

object BidirectionalFlow extends App {

  implicit val system = ActorSystem("bi-flow")
  implicit val materializer = ActorMaterializer()

  /*
  Example cryptography
   */
  def encrypt(n: Int)(s: String): String = s.map(c => (c + n).toChar)

  def decrypt(n: Int)(s: String): String = s.map(c => (c - n).toChar)
  //bidiflow

  val bidiflow = GraphDSL.create() {
    implicit builder =>
      val encFlowShape = builder.add(Flow[String].map(encrypt(3)))
      val decFlowShape = builder.add(Flow[String].map(decrypt(3)))
      //BidiShape(encFlowShape.in, encFlowShape.out, decFlowShape.in, decFlowShape.out)
      BidiShape.fromFlows(encFlowShape, decFlowShape)
  }

  val unencStrings = List("Andreea", "Radu", "Mao")
  val unencSource = Source(unencStrings)
  val encSource = Source(unencStrings.map(encrypt(3)))

  val cryptoGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val bidiShape = builder.add(bidiflow)
      val encSinkShape = builder.add(Sink.foreach[String](s => println(s"Encrypted: $s")))
      val decSinkShape = builder.add(Sink.foreach[String](s => println(s"Decrypted: $s")))
      unencSource ~> bidiShape.in1
      bidiShape.out1 ~> encSinkShape
      encSource ~> bidiShape.in2
      bidiShape.out2 ~> decSinkShape.in
      ClosedShape
    }
  )

  cryptoGraph.run()
}
