package akka.actors.testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{CallingThreadDispatcher, TestActorRef, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import scala.concurrent.duration._

class SyncTestingSpec extends WordSpecLike with BeforeAndAfterAll {

  implicit val system = ActorSystem("sync-spec")

  import SyncTestingSpec._

  override def afterAll(): Unit = system.terminate()

  "a counter" should {
    "synchronously increase its counter" in {
      val counter = TestActorRef[Counter](Props[Counter])
      counter ! Inc // counter has already received the message
      assert(counter.underlyingActor.c == 1)
    }

    "synchronously increase its counter at the call of the recv function" in {
      val counter = TestActorRef[Counter](Props[Counter])
      counter.receive(Inc)
      assert(counter.underlyingActor.c == 1)
    }

    "work on the calling thread dispatcher" in {
      val counter = system.actorOf(Props[Counter].withDispatcher(CallingThreadDispatcher.Id))
      val probe = TestProbe()
      probe.send(counter, Read)
      probe.expectMsg(Duration.Zero,0) // probe has already received the message bc counter is running on the calling thread
    }
  }


}

object SyncTestingSpec {

  case object Inc
  case object Read

  class Counter extends Actor {
    var c = 0
    override def receive: Receive = {
      case Inc => c += 1
      case Read => sender() ! c
    }
  }
}
