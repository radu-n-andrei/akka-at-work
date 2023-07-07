package akka.actors.faultTolerance

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props, Terminated}

object ActorLifecycle extends App {

  class LifecycleActor extends Actor with ActorLogging {

    override def receive: Receive = {
      case StartChild => context.actorOf(Props[LifecycleActor], "child")
    }

    override def postStop(): Unit = {
      log.info("I HAVE STOPPED")
    }

    override def preStart(): Unit = {
      log.info("I AM STARTING")
    }
  }

  case object StartChild

  case object Fail

  case object FailChild

  case object CheckChild

  case object Check

  case object LookInside

  case object ChildCount

  val system = ActorSystem("lifecycle-demo")
  //val parent = system.actorOf(Props[LifecycleActor], "parent")
  /*  parent ! StartChild
    parent ! PoisonPill*/

  /**
   * restart
   */
  class Child extends Actor with ActorLogging {
    override def receive: Receive = {
      case Fail =>
        log.warning("CHILD WILL FAIL NOW")
        throw new RuntimeException("I failed")
      case Check =>
        log.info("CHILD IS JUST FINE...but i am alone")
      case LookInside =>
        val innerChild = context.actorOf(Props[Child], "inner-child")
        context.watch(innerChild)
        context.become(enlightened(innerChild))
      case Terminated(actorRef) => log.info(s"My child ${actorRef.path} has been terminated")
      case ChildCount => log.info(s"I have ${context.children.size} kids of which i'm watching")
    }

    def enlightened(innerChild: ActorRef): Receive = {
      case Fail =>
        log.warning("CHILD WILL FAIL NOW")
        throw new RuntimeException("I failed")
      case Check =>
        innerChild ! Check
        log.info("CHILD IS JUST FINE")
    }

    override def preStart(): Unit = log.info("CHILD HAS STARTED")

    override def postStop(): Unit = log.info("CHILD HAS STOPPED")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      log.info(s"CHILD RESTARTING BECAUSE ${reason.getMessage}")
      //super.preRestart(reason, message)
    }

    override def postRestart(reason: Throwable): Unit = log.info("CHILD RESTARTED")
  }

  class Parent extends Actor with ActorLogging {
    val child = context.actorOf(Props[Child], "supervised-child")

    override def receive: Receive = {
      case FailChild => child ! Fail
      case CheckChild => child ! Check
      case LookInside => child ! LookInside
      case ChildCount => child ! ChildCount
    }
  }

  val superParent = system.actorOf(Props[Parent], "supervisor")
  superParent ! LookInside
  superParent ! CheckChild
  val innerKid = system.actorSelection("/user/supervisor/supervised-child/inner-child")
  superParent ! FailChild
  Thread.sleep(300)
  superParent ! ChildCount
  innerKid ! PoisonPill
}
