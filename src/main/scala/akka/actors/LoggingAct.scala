package akka.actors

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.event.Logging

object LoggingAct extends App {

  // #1 explicit logging
  class SimpleActorWithLog extends Actor {
    val logger = Logging(context.system, this)
    override def receive: Receive = {
      case message => logger.info(message.toString)
    }
  }

  val sys = ActorSystem("log-sys")
  val logs = sys.actorOf(Props[SimpleActorWithLog], "log-1")

  logs ! "this gets logged explicitly"

  class ActorWithLogging extends Actor with ActorLogging {
    override def receive: Receive = {
      case (a, b) => log.info("Two params: {} and {}", a, b)
      case message => log.info(message.toString)
    }
  }

  val log2 = sys.actorOf(Props[ActorWithLogging], "log-2")

  log2 ! "this logger was built in"
  log2 ! 2 -> "lolek"
}
