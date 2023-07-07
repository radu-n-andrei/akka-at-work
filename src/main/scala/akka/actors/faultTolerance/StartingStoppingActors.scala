package akka.actors.faultTolerance

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Kill, PoisonPill, Props, Terminated}

object StartingStoppingActors extends App {

  val system = ActorSystem("fault-tolerance-1")

  class Parent extends Actor with ActorLogging {

    import Parent._

    override def receive: Receive = withChildren(Map.empty)

    def withChildren(childMap: Map[String, ActorRef]): Receive = {
      case StartChild(name) => {
        log.info(s"starting child with $name")
        context.become(withChildren(childMap + (name -> context.actorOf(Props(new Child(name)), name))))
      }
      case StopChild(name) =>
        log.info(s"stopping child $name")
        val childOption = childMap.get(name)
        childOption.foreach(ref => context.stop(ref)) // async stop
        context.become(withChildren(childMap - name))
      case Stop =>
        log.info("stopping myself")
        context.stop(self)
    }
  }

  object Parent {
    case class StartChild(name: String)

    case class StopChild(name: String)

    case object Stop // stops itself
  }

  class Child(name: String) extends Actor with ActorLogging {
    override def receive: Receive = {
      case s => log.info(s"[$name] $s")
    }
  }

  import Parent._

  val parent = system.actorOf(Props[Parent], "parent")

  parent ! StartChild("child-1")
  val child = system.actorSelection("/user/parent/child-1")
  child ! "I'm alive"
  parent ! StopChild("child-1")
  //(1 to 50).foreach(_ => child ! "You still there?")
  parent ! StartChild("child-2")
  val child2 = system.actorSelection("/user/parent/child-2")
  child2 ! "Hey man"
  parent ! Stop
  child2 ! "Hey man"

  // other way to stop actors - special messages
  val child3 = system.actorOf(Props(new Child("orphan")), "timmy")
  child3 ! "hello my boy"
  child3 ! PoisonPill
  child3 ! "You still there?"

  val child4 = system.actorOf(Props(new Child("orphan2")), "jimmy")
  child4 ! "I barely knew you"
  child4 ! Kill

  // death watch
  class Watcher extends Actor with ActorLogging {
    import Parent._
    override def receive: Receive = {
      case StartChild(name) =>
        val c = context.actorOf(Props(new Child(name)), name)
        log.info(s"Started & watching $name")
        context.watch(c) // & unwatch for unsubscribe
      case Terminated(ref) =>
        log.info(s"The reference $ref has been stopped")
    }
  }

  val watcher = system.actorOf(Props[Watcher], "watcher")
  watcher ! StartChild("spy")
  val spy = system.actorSelection("/user/watcher/spy")
  Thread.sleep(100)
  spy ! PoisonPill
}
