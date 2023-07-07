package akka.steams.advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, Attributes, FlowShape, Inlet, Outlet, SinkShape, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler}

import java.util.concurrent.Executors
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Random, Success, Try}
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}

object CustomOperators extends App {
  implicit val system = ActorSystem("custom-ops")
  implicit val materializer = ActorMaterializer()

  // 1 - custom source which emits random numbers until canceled
  class RandomNumberGenerator(max: Int) extends GraphStage[SourceShape[Int]] {
    val outPort = Outlet[Int]("randomGenerator")
    val random = new Random()

    override def shape: SourceShape[Int] = SourceShape(outPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      // implement my logic here
      setHandler(outPort, new OutHandler {
        // when there is demand from downstream
        override def onPull(): Unit = {
          // emit a new element
          val nextNumber = random.nextInt(max)
          // push it out the outport
          push(outPort, nextNumber)
        }
      })
    }
  }

  val randomGeneratorSource = Source.fromGraph(new RandomNumberGenerator(100))
  //randomGeneratorSource.throttle(1, 1 second).runWith(Sink.foreach(println))

  // 2. custom sink that prints elements in batches
  class BatchSink(size: Int) extends GraphStage[SinkShape[Int]] {
    val inPort = Inlet[Int]("batcher")

    override def shape: SinkShape[Int] = SinkShape[Int](inPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      override def preStart(): Unit = {
        pull(inPort)
      }

      // mutable state
      val batch = new mutable.Queue[Int]
      setHandler(inPort, new InHandler {
        // when the upstream wants to send me an element
        override def onPush(): Unit = {
          val nextElement = grab(inPort)
          batch.enqueue(nextElement)
          if (batch.size >= size) {
            println("new batch: " + batch.dequeueAll(_ => true).mkString("[", "; ", "]"))
          }
          // send demand upstream
          pull(inPort)
        }

        override def onUpstreamFinish(): Unit = {
          if (batch.nonEmpty)
            println("new batch: " + batch.dequeueAll(_ => true).mkString("[", "; ", "]"))
          println("Stream finished")
        }
      })
    }
  }

  val batchSink = Sink.fromGraph(new BatchSink(10))

  //randomGeneratorSource.to(batchSink).run()

  /**
   * Exercise: custom flow - simple filter flow; generic custom component
   */
  class FilterFlow[T](pred: T => Boolean) extends GraphStage[FlowShape[T, T]] {
    val inPort = Inlet[T]("filter_in")
    val outPort = Outlet[T]("filter_out")

    override def shape: FlowShape[T, T] = FlowShape(inPort, outPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      setHandler(inPort, new InHandler {
        override def onPush(): Unit = {
          Try {
            val nextElement = grab(inPort)
            if (pred(nextElement)) {
              push(outPort, nextElement)
            } else {
              pull(inPort)
            }
          } match {
            case Failure(exception) => failStage(exception)
            case Success(_) =>
          }
        }
      })


      setHandler(outPort, new OutHandler {
        override def onPull(): Unit = {
          pull(inPort)
        }
      })
    }
  }

  val myFilterFlow = Flow.fromGraph(new FilterFlow[Int](_ < 50))

  //randomGeneratorSource.throttle(1, 1 second).via(myFilterFlow).to(Sink.foreach(println)).run

  /**
   * Materialized values in graph stages - flow that counts items as well
   */
  class CounterFlow[T] extends GraphStageWithMaterializedValue[FlowShape[T, T], Future[Int]] {
    val inPort = Inlet[T]("counter_in")
    val outPort = Outlet[T]("counter_out")
    override val shape = FlowShape(inPort, outPort)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Int]) = {
      val promise = Promise[Int]
      val logic = new GraphStageLogic(shape) {
        var counter = 0
        setHandler(outPort, new OutHandler {
          override def onPull(): Unit = pull(inPort)

          override def onDownstreamFinish(): Unit = {
            promise.success(counter)
            super.onDownstreamFinish()
          }
        })
        setHandler(inPort, new InHandler {
          override def onPush(): Unit = {
            val v = grab(inPort)
            counter += 1
            push(outPort, v)
          }

          override def onUpstreamFinish(): Unit = {
            promise.success(counter)
            super.onUpstreamFinish()
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            promise.failure(ex)
            super.onUpstreamFailure(ex)
          }
        })
      }

      (logic, promise.future)
    }
  }

  val counterFlow = Flow.fromGraph(new CounterFlow[Int])

  val f = Source(1 to 100).viaMat(counterFlow)(Keep.right).to(Sink.ignore).run()

  f.onComplete {
    case Success(v) => println(s"We got a total of $v elements")
    case Failure(_) => println("Failed to get elements")
  }(system.dispatcher)
}
