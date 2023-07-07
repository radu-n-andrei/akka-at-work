package akka.actors.testing

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class TestProbeSpec extends TestKit(ActorSystem("test-probe-spec")) with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import TestProbeSpec._
  "A master actor" should {
    "register a slave" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave") // special actor w/ assertion capabilities
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)
    }

    "send the work to the slave actor" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave")
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)
      val workloadString = "I love akka"
      master ! Work(workloadString)
      slave.expectMsg(SlaveWork(workloadString, testActor))
      slave.reply(WorkCompleted(3, testActor))
      expectMsg(Report(3))
    }

    "aggregate data correctly" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave")
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)
      val workloadString1 = "I love akka"
      val workloadString2 = "for real"
      master ! Work(workloadString1)
      master ! Work(workloadString2)
      slave.receiveWhile() {
        case SlaveWork(`workloadString1`, `testActor`) => slave.reply(WorkCompleted(3, testActor))
        case SlaveWork(`workloadString2`, `testActor`) => slave.reply(WorkCompleted(2, testActor))
      }
      expectMsg(Report(3))
      expectMsg(Report(5))
    }
  }
}

object TestProbeSpec {
  // scenario send some work to the master; master sends to the slave; the salve procs the work and replies to master; master aggregates results
  // master sends the total word count to the original requester

  case class Register(slaveRef: ActorRef)
  case class Work(text: String)
  case class SlaveWork(text: String, originalRequester: ActorRef)
  case class WorkCompleted(count: Int, originalRequester: ActorRef)
  case class Report(totalCount: Int)
  case object RegistrationAck

  class Master extends Actor {
    override def receive: Receive = {
      case Register(slaveRef) =>
        sender() ! RegistrationAck
        context.become(onLine(slaveRef, 0))
      case _ => // ignore
    }

    def onLine(slaveRef: ActorRef, totalWorkCount: Int): Receive  = {
      case Work(text) => slaveRef ! SlaveWork(text, sender())
      case WorkCompleted(count, originalRequester) =>
        val newTotal = totalWorkCount + count
        originalRequester ! Report(newTotal)
        context.become(onLine(slaveRef, newTotal))
    }

  }

  // actor slave prob defined
}
