package akka.steams.advanced

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}

import scala.util.Success

object Substreams extends App {
  implicit val system = ActorSystem("substreams")
  implicit val materializer = ActorMaterializer()

  // 1 - grouping a stream by a certain function
  val wordSource = Source(List("akka", "is", "amazing", "learning", "substreams"))
  val groups = wordSource.groupBy(30, w => if (w.isEmpty) '\u0000' else w.toLowerCase.charAt(0))
  groups.to(Sink.fold(0)((count, w) => {
    println(s"I just received $w, count is ${count + 1}")
    count + 1
  })).run()

  // 2 - merge substreams back
  val textSource = Source(List(
    "I love akka streams",
    "this is amazing",
    "learning from rock the jvm"
  ))

  val f = textSource.groupBy(2, w => w.length % 2).map(_.length)
    .mergeSubstreamsWithParallelism(2).toMat(Sink.reduce[Int](_ + _))(Keep.right).run()

  f.onComplete {
    case Success(v) => println(s"total is $v")
    case _ => println("failed")
  }(system.dispatcher)

  // 3 - splitting a stream into substreams when a condition is met
  val text = "I love akka streams\n" +
    "this is amazing\n" +
    "learning from rock the jvm\n"

  val anotherCharCountFuture = Source(text.toList).splitWhen(c => c == '\n').filter(_ != '\n').map(_ => 1)
    .mergeSubstreams.toMat(Sink.reduce[Int](_ + _))(Keep.right).run()

  anotherCharCountFuture.onComplete {
    case Success(value) => println(s"a new total is $value")
    case _ => "failed yet again"
  }(system.dispatcher)

  // 4 - flattening
  val simpleSource = Source(1 to 5)
  simpleSource.flatMapConcat(x => Source(x to (3 * x))).runWith(Sink.foreach(println))
  simpleSource.flatMapMerge(2, x => Source(x to (3 * x))).runWith(Sink.foreach(println))
}
