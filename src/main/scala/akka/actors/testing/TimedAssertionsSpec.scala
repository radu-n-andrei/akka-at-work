package akka.actors.testing

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class TimedAssertionsSpec extends TestKit(ActorSystem("timed-assert",
  ConfigFactory.load().getConfig("timeboxedConfig")))
  with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  import TimedAssertionsSpec._

  "a worker actor" should {
    val workerActor = system.actorOf(Props[WorkerActor])
    "reply with 1 in a timely manner" in {
      within(500 millis, 1 second) { // timeboxing
        workerActor ! "work"
        expectMsg(WorkResult(1))
      }
    }

    "reply with a sequence" in {
      within(1 second) {
        workerActor ! "work-sequence"
        val results: Seq[Int] = receiveWhile[Int](max = 2 seconds, idle = 500 millis, messages = 10) {
          case WorkResult(result) => result
        }
        assert(results.sum > 50)
      }
    }

    "reply to a test probe in a timely manner" in {
      within(1 second) {
        val probe = TestProbe()
        probe.send(workerActor, "work")
        probe.expectMsg(WorkResult(1)) // probe has a 3k ms timeout; probe's timeout doesn't work with within time
      }
    }

    // the timeout config doesn't apply to actors created through the system
    "reply through another actor in a timely manner" in {
      val dummy = system.actorOf(Props(new DummyActor(workerActor, testActor)))
      within(1 second) {
        dummy ! "work"
        expectMsg(WorkResult(1))
      }
    }

  }

}

object TimedAssertionsSpec {

  case class WorkResult(result: Int)

  class WorkerActor extends Actor {
    override def receive: Receive = {
      case "work" =>
        Thread.sleep(500) // long computation
        sender() ! WorkResult(1)
      case "work-sequence" =>
        val r = new Random()
        (1 to 10).foreach { _ =>
          Thread.sleep(r.nextInt(50))
          sender() ! WorkResult(10)
        }
    }
  }

  class DummyActor(worker: ActorRef, mainSender: ActorRef) extends Actor {
    override def receive: Receive = {
      case "work" => {
        println("sending over the work")
        worker ! "work"
      }
      case a: WorkResult => {
        println("sending over the result")
        mainSender ! a
      }
    }
  }

}
