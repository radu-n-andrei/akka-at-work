package akka.actors.infra

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.routing.{RoundRobinPool, RoundRobinRoutingLogic, Router}
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

object Dispatchers extends App {

  class Counter extends Actor with ActorLogging {
    override def receive: Receive = count(0)

    def count(i: Int): Receive = {
      case m => log.info(s"$m - ${i + 1}")
        context.become(count(i + 1))
    }
  }

  val system = ActorSystem("dispatcher-demo", ConfigFactory.load().getConfig("dispatcher-demo"))

  // method 1 - in code
  val counters = (1 to 10).map(i => system.actorOf(Props[Counter].withDispatcher("my-dispatcher"), s"counter-$i"))
  val r = new Random()
  /*(1 to 1000).foreach { i =>
    counters(r.nextInt(10)) ! i
  }*/

  // method 2 - from config
  val specialActor = system.actorOf(Props[Counter], "second-dispatcher") // system needs the config

  specialActor ! "hello"

  case class SlowMessage(m: String)

  // Dispatchers implement the ExecutionContext trait
  class DBActor extends Actor with ActorLogging {
    // how to avoid thread starvation
    // 1 with a dedicated dispatcher that will be used for slow operations (see SlowActor)

    // 2 with a router, routing slow messages to a separate child
    val router = context.actorOf(RoundRobinPool(3).props(Props[SlowActor]))

    override def receive: Receive = {
      case SlowMessage(m) => router ! m
      case m => log.info(m.toString)
    }
  }

  class SlowActor extends Actor with ActorLogging {
    implicit val executionContext: ExecutionContext = context.system.dispatchers.lookup("my-dispatcher")

    override def receive: Receive = {
      case m => Future { // don't do this my man - might starve dispatcher
        // wait on a resource
        Thread.sleep(5000)
        log.info(s"Success: $m")
      }
    }
  }

  val dbActor = system.actorOf(Props[DBActor])
  val dbActorRouter = system.actorOf(RoundRobinPool(3).props(Props[DBActor]).
    withDispatcher("my-dispatcher"))
  dbActorRouter ! "this one is going to be slow"
  val nonBlockingActor = system.actorOf(Props[Counter])
  //dbActor ! "I got this dispatcher stuff"



  (1 to 1000).foreach(i => {
    dbActor ! SlowMessage(s"message $i")
    dbActor ! s"message $i"
  })
}
