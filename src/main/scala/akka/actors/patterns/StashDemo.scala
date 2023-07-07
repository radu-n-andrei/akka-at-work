package akka.actors.patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Stash}

object StashDemo extends App {

  /*
  ResourceActor
  -open -> it can receive r/w requests
  - otherwise it will postpone requests
  - starts as closed. read/write are postponed
  - on open switches to the open state
  - when open it can handle Read/Write/Close
   */

  case object Open

  case object Close

  case object Read

  case class Write(t: String)

  class ResourceActor extends Actor with ActorLogging with Stash {

    override def receive: Receive = closed(List.empty[String])

    private def opened(s: List[String]): Receive = {
      case Close =>
        log.info("closing")
        unstashAll()
        context.become(closed(s))
      case Read =>
        log.info(s"reading ${s.headOption}")
        context.become(opened(if (s.isEmpty) List.empty else s.tail))
      case Write(t) =>
        log.info(s"writing ${t}")
        context.become(opened(t +: s))
      case m =>
        log.info(s"Stashing ${m} can't handle it while open")
        stash()
    }

    private def closed(s: List[String]): Receive = {
      case Open =>
        log.info("opening")
        unstashAll()
        context.become(opened(s))
        println("LOOOOOOL")
      case message => log.info(s"Stashing $message can't handle it while closed")
        stash()
    }
  }

  val system = ActorSystem("stashing-demo")

  val resourceActor = system.actorOf(Props[ResourceActor])

  resourceActor ! Read
  resourceActor ! Open
  resourceActor ! Open
  resourceActor ! Write("Hello!")
  resourceActor ! Close
  resourceActor ! Read

  // stashing Read -> opening -> reading None -> stash Open -> write hello -> closing -> opening -> read hello

}
