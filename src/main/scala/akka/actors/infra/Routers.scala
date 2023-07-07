package akka.actors.infra

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Terminated}
import akka.routing.{ActorRefRoutee, Broadcast, FromConfig, RoundRobinGroup, RoundRobinPool, RoundRobinRoutingLogic, Router}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.language.postfixOps

object Routers extends App {

  case object StopChild
  /*
   #1 manual router
   */
  class Parent extends Actor with ActorLogging {
    private val children = (1 to 5).map { i =>
      val child = context.actorOf(Props[Child], s"child-$i")
      context.watch(child)
      ActorRefRoutee(child)
    }

    override def receive: Receive = routing(router = Router(RoundRobinRoutingLogic(), children))

    def routing(router: Router): Receive = {
      case Terminated(ref) =>
        log.info(s"Creating: ${ref.path.name}")
        val newChild = context.actorOf(Props[Child], s"${ref.path.name}")
        context.watch(newChild)
        context.become(routing(router.removeRoutee(ref).addRoutee(newChild)))
      case m => router.route(m, sender())
    }
  }

  class Child extends Actor with ActorLogging {
    override def receive: Receive = {
      case StopChild => context.stop(self)
      case m => log.info(m.toString)
    }

    override def postStop(): Unit = log.info(s"Stopping ${self.path.name}")
  }

  val system = ActorSystem("router-demo", ConfigFactory.load().getConfig("routers-demo"))
  val manualParent = system.actorOf(Props[Parent], "parent-1")

  (1 to 5).foreach(i => manualParent ! s"message${i}")


  system.scheduler.scheduleOnce(5 seconds){
    val child3 = system.actorSelection("/user/parent-1/child-3")
    child3 ! StopChild
  }(system.dispatcher)

  system.scheduler.scheduleOnce(8 seconds) {
    (1 to 5).foreach(i => manualParent ! s"message${i}")
  }(system.dispatcher)

  /**
   * #2 - router with its own children, POOL router
   */
  val poolParent = system.actorOf(RoundRobinPool(5).props(Props[Child]), "pool-parent")
  (1 to 10).foreach(s => poolParent ! s"Message$s")

  val poolParentFromConfig = system.actorOf(FromConfig.props(Props[Child]), "pool-parent-2")
  (1 to 10).foreach(s => poolParentFromConfig ! s"Message$s")

  /**
   * #3 GROUP router - actors created elsewhere
   */
  val children = (1 to 5).map(i => system.actorOf(Props[Child], s"child-outer-$i")).toList
  val kiddyPaths = children.map(_.path.toString)
  val groupParent = system.actorOf(RoundRobinGroup(kiddyPaths).props())
  (1 to 10).foreach(s => groupParent ! s"Message$s")
  val groupParentFromConfig = system.actorOf(FromConfig.props(), "group-parent-2")
  (1 to 10).foreach(s => groupParentFromConfig ! s"Message$s")

  /**
   * Special messages
   */
  groupParentFromConfig ! Broadcast("Hello everyone")
  // PoisonPill and Kill are not routed
  // Add/Remove/GetRoutee are handled only by the routing actor
}
