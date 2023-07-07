package akka.actors.faultTolerance

import akka.actor.SupervisorStrategy.{Escalate, Restart, Resume, Stop}
import akka.actor.{Actor, ActorRef, ActorSystem, AllForOneStrategy, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class SupervisionSpec extends TestKit(ActorSystem("test-life")) with WordSpecLike with ImplicitSender with BeforeAndAfterAll {

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  import SupervisionSpec._

  "a supervisor" should {
    "resume its child in case of a minor fault" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]
      child ! "I love Akka"
      child ! "Yes i do"
      child ! Report
      expectMsg[Int](6)
      child ! "This should really be longer than 20 chars"
      child ! Report
      expectMsg[Int](6)
    }

    "restart its child in case of an empty string" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]
      child ! "I love Akka"
      child ! "Yes i do"
      child ! Report
      expectMsg[Int](6)
      child ! ""
      child ! Report
      expectMsg[Int](0)
    }

    "terminate its child in case of a major error" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]
      child ! "I love Akka"
      child ! "Yes i do"
      child ! Report
      expectMsg[Int](6)
      watch(child)
      child ! "please don't do this"
      val terminatedMessage = expectMsgType[Terminated]
      assert(terminatedMessage.actor == child)
    }

    "escalate an error when message is unknown" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]
      child ! "I love Akka"
      child ! "Yes i do"
      child ! Report
      expectMsg[Int](6)
      watch(child)
      child ! 43
      expectTerminated(child)
    }
  }

  "a no death supervisor" should {
    "not kill children on restarting" in {
      val supervisor = system.actorOf(Props[NoDeathOnRestartSupervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]
      child ! "I love Akka"
      child ! "Yes i do"
      child ! Report
      expectMsg[Int](6)
      child ! 11
      child ! Report
      expectMsg[Int](0)
      child ! "And some more"
      child ! Report
      expectMsg[Int](3)
    }
  }

  "an all for one supervisor" should {
    "apply the all-for-one" in {
      val supervisor = system.actorOf(Props[AllForOneSupervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]
      supervisor ! Props[FussyWordCounter]
      val child2 = expectMsgType[ActorRef]
      child2 ! "Hello there"
      child2 ! Report
      expectMsg(2)
      EventFilter[NullPointerException]() intercept {
        child ! ""
      }
      Thread.sleep(200)
      child2 ! Report
      expectMsg(0)
    }
  }

}

object SupervisionSpec {

  case object Report

  class FussyWordCounter extends Actor {
    override def receive: Receive = startCounting(0)

    def startCounting(count: Int): Receive = {
      case Report => sender() ! count
      case "" => throw new NullPointerException("sentence is empty")
      case w: String => if (w.length > 20) throw new RuntimeException("sentence is too big")
      else if (!Character.isUpperCase(w(0))) throw new IllegalArgumentException("sentence must start with uppercase")
      else context.become(startCounting(count + w.split(" ").length))
      case _ => throw new Exception("can not process anything else")
    }
  }

  class Supervisor extends Actor {

    override val supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _: NullPointerException => Restart
      case _: IllegalArgumentException => Stop
      case _: RuntimeException => Resume
      case _: Exception => Escalate
    }

    override def receive: Receive = {
      case props: Props =>
        val child = context.actorOf(props)
        sender() ! child
    }
  }

  class NoDeathOnRestartSupervisor extends Supervisor {
    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      //
    }
  }

  class AllForOneSupervisor extends Supervisor {
    // applies this for all actors, not just the one that failed
    override val supervisorStrategy = AllForOneStrategy() {
      case _: NullPointerException => Restart
      case _: IllegalArgumentException => Stop
      case _: RuntimeException => Resume
      case _: Exception => Escalate
    }
  }

}
