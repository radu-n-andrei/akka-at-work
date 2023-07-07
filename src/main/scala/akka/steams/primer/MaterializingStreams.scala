package akka.steams.primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.util.{Failure, Success, Try}

object MaterializingStreams extends App {
  implicit val system = ActorSystem("mat-streams")
  implicit val materializer = ActorMaterializer()

  //val simpleGraph = Source(1 to 10).to(Sink.foreach[Int](println))

  //val simpleMaterializedValue = simpleGraph.run()

  val source = Source(1 to 10)
  val sink = Sink.reduce[Int](_ + _)
  /*val sumFuture = source.runWith(sink)

  sumFuture.onComplete {
    case Success(value) => println(s"The sum of the elems is $value")
    case Failure(exception) => println(s"Failed: $exception")
  }(system.dispatcher)*/

  // choosing materialized values
  val simpleSource = Source(1 to 10)
  val simpleFlow = Flow[Int].map(_ * 2)
  val simpleSink = Sink.foreach[Int](println)
  val sGraph = simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right)
  /*sGraph.run().onComplete {
    case Success(_) => println("FINISHED")
    case Failure(_) => println("ERROR")
  }(system.dispatcher)*/

  // sugars
  //val sum = Source(1 to 10).runWith(sink) // the same as source.toMat(Sink.reduce)(Keep.right).run()
  Source(1 to 10).runReduce[Int](_ + _) // same
  // backwards
  //val x = Sink.foreach[Int](println).runWith(Source.single(11))

  // both ways
  //Flow[Int].map(_ * 2).runWith(simpleSource, simpleSink)

  /**
   * Exercises (as many ways as possible)
   * 1. last element out of the source
   * 2. total word count out of a stream of sentences
   */
  val intComplete: PartialFunction[Try[Int], Unit] = {
    case Success(l) => println(s"The result is ${l}")
    case Failure(_) => println("Failed to get result")
  }
  val intSource = Source(1 to 10)
  val lastSink = Sink.last[Int]
  intSource.toMat(Sink.last[Int])(Keep.right).run().onComplete(intComplete)(system.dispatcher)
  intSource.runWith(lastSink).onComplete(intComplete)(system.dispatcher)

  val sentenceSource = Source(List("this has 4 words", "this has 3", "only 2", "but this one has 5"))
  val reduceSink = Sink.fold[Int, String](0)((acc, s) => acc + s.split(" ").length)
  val identSink = Sink.head[Int]
  val printSink = Sink.foreach[Int](println)
  val countFlow = Flow[String].map(_.split(" ").length)
  val reduceFlow = Flow[Int].reduce(_ + _)
  val foldFlow = Flow[String].fold[Int](0)((c, s) => c + s.split(" ").length)

  sentenceSource.via(countFlow).via(reduceFlow).toMat(identSink)(Keep.right).run().onComplete(intComplete)(system.dispatcher)
  sentenceSource.toMat(reduceSink)(Keep.right).run().onComplete(intComplete)(system.dispatcher)
  countFlow.via(reduceFlow).runWith(sentenceSource, printSink)
  sentenceSource.via(countFlow).via(reduceFlow).runWith(printSink)
  sentenceSource.via(countFlow).runReduce[Int](_ + _).onComplete(intComplete)(system.dispatcher)
  foldFlow.runWith(sentenceSource, identSink)._2.onComplete(intComplete)(system.dispatcher)




}
