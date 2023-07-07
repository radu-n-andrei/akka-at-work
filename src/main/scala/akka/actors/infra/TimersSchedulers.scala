package akka.actors.infra

import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable, Props, Timers}

import scala.concurrent.duration._
import scala.language.postfixOps


object TimersSchedulers extends App {

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case m => log.info(m.toString)
    }
  }

  val system = ActorSystem("schedulers-timers")
  val simpleActor = system.actorOf(Props[SimpleActor], "simple-1")

  system.log.info("Scheduling reminder for simple actor")

  import system.dispatcher

  system.scheduler.scheduleOnce(1 second) {
    simpleActor ! "reminder"
  }

  val routine: Cancellable = system.scheduler.schedule(1 second, 2 seconds) {
    simpleActor ! "heartbeat"
  }

  system.scheduler.scheduleOnce(8 seconds) {
    routine.cancel()
  }

  /**
   * Exercise - implement a self closing actor
   * - if the actor receives a message, you have 1 second to send it another message, if the time window expires the actor will stop itself
   * - if you send another message the time window is reset
   */

  case object SelfShutdown

  class SelfClosingActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case m => log.info(m.toString)
        context.become(pendingAnotherMessage(shutdownRoutine))
    }

    def pendingAnotherMessage(cancellable: Cancellable): Receive = {
      case SelfShutdown => context.stop(self)
      case m => log.info(m.toString)
        cancellable.cancel()
        context.become(pendingAnotherMessage(shutdownRoutine))
    }

    private def shutdownRoutine: Cancellable = context.system.scheduler.scheduleOnce(3 seconds) {
      self ! SelfShutdown
    }

    override def postStop(): Unit = log.info("Shutting down...")
  }

  val selfClosingActor = system.actorOf(Props[SelfClosingActor])

  val keepAlive = system.scheduler.schedule(1 second, 2 seconds) {
    selfClosingActor ! "live!"
  }

  system.scheduler.scheduleOnce(15 seconds) {
    keepAlive.cancel()
  }

  case object TimerKey

  case object Start

  case object Reminder

  class TimerBasedHeartbeatActor extends Actor with ActorLogging with Timers {
    timers.startSingleTimer(TimerKey, Start, 1 second)

    override def receive: Receive = {
      case Start => log.info("Bootstrapping...")
        timers.startPeriodicTimer(TimerKey, Reminder, 1 second) // same key cancels previous timer
      case Reminder => log.info("I am alive")
      case SelfShutdown => log.warning("Stopping...")
        timers.cancel(TimerKey)
        context.stop(self)
    }
  }

   val timerBasedHeartbeatActor = system.actorOf(Props[TimerBasedHeartbeatActor])

   system.scheduler.scheduleOnce(8 seconds) {
     timerBasedHeartbeatActor ! SelfShutdown
   }
  case object SelfClosingTimerKey

  class TimerSelfClosingActor extends Actor with ActorLogging with Timers {

    override def receive: Receive = {
      case SelfShutdown => context.stop(self)
      case m => log.info(m.toString)
        timers.startSingleTimer(SelfClosingTimerKey, SelfShutdown, 3 seconds)
    }

    override def postStop(): Unit = log.info("Shutting down...")
  }

  val timerSelfClosingActor = system.actorOf(Props[TimerSelfClosingActor])

  val timerRoutine = system.scheduler.schedule(1 second, 2 seconds) {
    timerSelfClosingActor ! "Hello!"
  }

  system.scheduler.scheduleOnce(15 seconds) {
    timerRoutine.cancel()
  }


}
