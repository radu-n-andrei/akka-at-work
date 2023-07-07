package akka.actors.patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Stash}
import scala.concurrent.duration._
import scala.language.postfixOps

object StashRestart extends App {

  class SimpleActor extends Actor with ActorLogging with Stash {

    override def receive: Receive = {
      case "shutdown" => throw new RuntimeException("let's restart")
      case m =>
        log.info(s"Stashing: ${m.toString}")
        stash()
    }
  }

  val system = ActorSystem("stash-restart")
  val sa = system.actorOf(Props[SimpleActor])

  sa ! "hello there"
  sa ! "still stashing"
  sa ! "sup"
  sa ! "shutdown"
  system.scheduler.scheduleOnce(5 seconds) {
    sa ! "back again"
  }(system.dispatcher)

}
