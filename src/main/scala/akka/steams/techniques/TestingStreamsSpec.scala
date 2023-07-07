package akka.steams.techniques

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.Await
import scala.util.{Failure, Success}

class TestingStreamsSpec extends TestKit(ActorSystem("testing-streams")) with WordSpecLike with BeforeAndAfterAll {

  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "A simple stream" should {
    "satisfy basic assertions" in {
      val simpleSource = Source(1 to 10)
      val simpleSink = Sink.fold[Int, Int](0)(_ + _)
      val sumFuture = simpleSource.toMat(simpleSink)(Keep.right).run()
      val sum = Await.result(sumFuture, 2 seconds)
      assert(sum == 55)
    }

    "integrate with test actors via materialized values" in {
      import akka.pattern.pipe
      import system.dispatcher
      val simpleSource = Source(1 to 10)
      val simpleSink = Sink.fold[Int, Int](0)(_ + _)
      val probe = TestProbe()
      simpleSource.toMat(simpleSink)(Keep.right).run().pipeTo(probe.ref)
      probe.expectMsg(55)
    }

    "integrate with a test actor based sink" in {
      val simpleSource = Source(1 to 5)
      val simpleFlow = Flow[Int].scan[Int](0)(_ + _)
      val streamUnderTest = simpleSource.via(simpleFlow)
      val probe = TestProbe()
      val probeSink = Sink.actorRef(probe.ref, "whatever completion message")
      streamUnderTest.to(probeSink).run()
      probe.expectMsgAllOf(0, 1, 3, 6, 10, 15)
    }

    "integrate with streams testkit sink" in {
      val sourceUnderTest = Source(1 to 5).map(_ * 2)
      val testSink = TestSink.probe[Int]
      val materializedTestVal = sourceUnderTest.runWith(testSink)
      materializedTestVal.request(5).expectNext(2, 4, 6, 8, 10).expectComplete()
    }

    "integrate with streams testkit source" in {
      import system.dispatcher
      val sinkUnderTest = Sink.foreach[Int] {
        case 13 => throw new RuntimeException()
        case _ =>
      }
      val testSource = TestSource.probe[Int]
      val materialized = testSource.toMat(sinkUnderTest)(Keep.both).run()
      val (testPublisher, resultFuture) = materialized
      testPublisher.sendNext(1).sendNext(5).sendNext(13).sendComplete()

      resultFuture.onComplete {
        case Success(_) => fail("The sink under test should have thrown an exception on 13")
        case Failure(_) => // ok
      }
    }

    "test flows with a test source and a test sink" in {
      val flowUnderTest = Flow[Int].map(_ * 2)
      val testSource = TestSource.probe[Int]
      val testSink = TestSink.probe[Int]

      val materialized = testSource.via(flowUnderTest).toMat(testSink)(Keep.both).run()
      val (source, sink) = materialized
      source.sendNext(1).sendNext(5).sendNext(42).sendNext(99).sendComplete()
      sink.request(4).expectNext(2, 10, 84, 198).expectComplete()
    }

  }

}
