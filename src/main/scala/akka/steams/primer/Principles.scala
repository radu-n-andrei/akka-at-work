package akka.steams.primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object Principles extends App {

  implicit val system = ActorSystem("principles")
  implicit val materializer = ActorMaterializer()

  // source
  val source = Source(1 to 10)
  // sink
  val sink = Sink.foreach[Int](println)
  // connect the 2
  val graph = source.to(sink)
  //graph.run()
  // flows
  val flow = Flow[Int].map(_ + 1)
  val sourceWithFlow = source.via(flow)
  val flowWithSink = flow.to(sink)

  // equivalent calls
  //sourceWithFlow.to(sink).run()
  //source.to(flowWithSink).run()
  //source.via(flow).to(sink).run()

  //val illegalSource = Source.single[String](null) // null are not allowed in
  //illegalSource.to(Sink.foreach(println)).run()

  /*
  various sources
   */
  val finiteSource = Source.single(1)
  val anotherFiniteSource = Source(List(1, 2, 3, 4))
  val emptySource = Source.empty[Int]
  val infiniteSource = Source(LazyList.from(1)) // do not confuse akka stream with a collection stream
  val futureSource = Source.fromFuture(Future {
    41
  })

  /*
  sinks
   */
  val ignoreSink = Sink.ignore
  val foreachSink = Sink.foreach[Int](println)
  val headSink = Sink.head[Int] // retrieves the head and closes the stream
  val foldSink = Sink.fold[Int, Int](0)((a, b) => a + b)

  /*
  flows - usually mapped to collection operators
   */
  val mapFlow = Flow[Int].map(_ * 3)
  val takeFlow = Flow[Int].take(5)
  // + drop, filter etc ... but not flatMap

  val tripleFlowGraph = source.via(mapFlow).via(takeFlow).to(sink)
  //tripleFlowGraph.run()

  // sugar
  val mapSource = Source(1 to 10).map(x => x * 2) // same as Source(1 to 10).via(Flow[Int].map(_ * 2))
  //mapSource.runForeach(println) // same as mapSource.to(Sink.foreach[Int](println)).run()

  // OPERATORS = components

  /**
   * Exercise - stream names of persons, then keep first 2 names with length > 5 then print
   */
  case class Person(name: String, age: Int)
  val pList = List(Person("Andreea", 33), Person("Radu", 34), Person("Maorilian", 5))
  Source(pList).filter(_.name.length > 5).map[String](_.name).take(2).runForeach(println)
  Source(pList).via(Flow[Person].filter(_.name.length > 5)).via(Flow[Person].map(_.name)).via(Flow[String].take(2)).to(Sink.foreach[String](println)).run
}
