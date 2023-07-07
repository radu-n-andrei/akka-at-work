package akka.actors

import ChildLabour.WCParent.{Initialise, WCReply, WCTask}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}


/**
 * DISTRIBUTED WORK COUNTING
 * actor hierarchy with workers managed by a parent w/ RR
 */

object ChildLabour extends App {

  class Requester(parentRef: ActorRef) extends Actor {
    override def receive: Receive = requesting(0)

    def requesting(id: Int): Receive = {
      case m: String => parentRef ! WCTask(id, m)
        context.become(requesting(id + 1))
      case WCReply(i, count) => println(s"[req] Just got answer for $i: $count")
    }
  }

  object Requester {
    def props(parentRef: ActorRef): Props = Props(new Requester(parentRef))
  }


  class WCParent extends Actor {
    override def receive: Receive = idle

    def idle: Receive = {
      case Initialise(nChildren) =>
        val kids = (1 to nChildren).map(i => context.actorOf(Props[WCChild], s"child-worker-$i")).toList
        context.become(initialised(kids, 0))
    }

    def initialised(workers: List[ActorRef], currentIndex: Int): Receive = {
      case WCTask(id, t) => workers(currentIndex) forward WCTask(id, t)
        context.become(initialised(workers, (currentIndex + 1) % workers.size))
    }
  }

  object WCParent {
    case class Initialise(nChildren: Int) // create nChildren WCChild actor instances

    case class WCTask(taskId: Int, text: String)

    case class WCReply(taskId: Int, count: Int)
  }

  class WCChild extends Actor {
    override def receive: Receive = {
      case WCTask(tId, t) => {
        println(s"[${self.path}] Processing $t")
        sender() ! WCReply(tId, t.split(' ').length)
      }
    }
  }

  val sys = ActorSystem("child-labour")
  val parent = sys.actorOf(Props[WCParent], "bad-parent")
  val req = sys.actorOf(Requester.props(parent), "requester-1")

  parent ! Initialise(3)
  req ! "how many words are these"
  req ! "these are 3"
  req ! "just"
  req ! "mao are blana foarte moale iarna"
}
