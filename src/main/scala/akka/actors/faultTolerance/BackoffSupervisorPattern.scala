package akka.actors.faultTolerance

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, Props}
import akka.pattern.{Backoff, BackoffOpts, BackoffSupervisor}

import java.io.File
import scala.concurrent.duration._
import scala.io.Source
import scala.language.postfixOps


object BackoffSupervisorPattern extends App {

  case object ReadFile

  class FilebasedPersistentActor extends Actor with ActorLogging {

    override def receive: Receive = {
      case ReadFile => {
        val dataSource = Source.fromFile(new File("src/main/resources/testfiles/important_data.txt"))
        log.info(s"I've been reading a lot: ${dataSource.getLines().toList}")
        //context.become(reading(dataSource))
      }
    }

    override def preStart(): Unit = log.info("STARTING...")

    override def postStop(): Unit = log.info("STOPPING...")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = log.warning("RESTARTING...")

    //def reading(dataSource: Source): Receive = {}
  }

  val system = ActorSystem("backoff")
  /*val f1 = system.actorOf(Props[FilebasedPersistentActor], "reader-1")
  f1 ! ReadFile

  val simpleSupervisorProps = BackoffSupervisor.props(
    BackoffOpts.onFailure(
      Props[FilebasedPersistentActor],
      "simple-backoff-actor",
      3 seconds,
      30 seconds,
      0.2
    )
  )
  // actor with a child; forwards messages to child; restart on default
  val simpleBackoffSupervisor = system.actorOf(simpleSupervisorProps, "simple-supervisor")

  simpleBackoffSupervisor ! ReadFile

  val stopSupervisorProps = BackoffSupervisor.props(
    BackoffOpts.onStop(
      Props[FilebasedPersistentActor],
      "stop-backoff-actor",
      3 seconds,
      30 seconds,
      0.2
    ).withSupervisorStrategy(
      OneForOneStrategy() {
        case _ => Stop
      }
    )
  )

  val stopSupervisor = system.actorOf(stopSupervisorProps, "stop-supervisor")

  stopSupervisor ! ReadFile*/


  class EagerFileBasedPersistentActor extends FilebasedPersistentActor {
    override def preStart(): Unit = {
      log.info("EAGER ACTOR STARTING")
      context.become(withFile(Source.fromFile(new File("src/main/resources/testfiles/important_data.txt"))))
    }

    override def receive: Receive = {
      _ => log.error("shouldn't be the case")
    }

    def withFile(dataSource: Source): Receive = {
      case ReadFile => log.info(s"I have read some interesting things: ${dataSource.getLines().toList}")
    }
  }

  //val eager = system.actorOf(Props[EagerFileBasedPersistentActor], "eager-beaver")
  val repeatedSupervisorProps = BackoffSupervisor.props(
    BackoffOpts.onStop(
      Props[EagerFileBasedPersistentActor],
      "backoff-eager",
      1 seconds,
      30 seconds,
      0.1
    )
  )

  val repeatedSupervisor = system.actorOf(repeatedSupervisorProps, "repeated-supervisor")



}
