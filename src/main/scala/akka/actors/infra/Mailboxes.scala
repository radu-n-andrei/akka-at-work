package akka.actors.infra

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.dispatch.{ControlMessage, PriorityGenerator, UnboundedPriorityMailbox}
import com.typesafe.config.{Config, ConfigFactory}

object Mailboxes extends App {

  val system = ActorSystem("mailbox-demo", ConfigFactory.load().getConfig("mailboxes-demo"))

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case m => log.info(m.toString)
    }
  }

  /**
   * Use case 1 - custom priority mailbox; support tickets: P0, P1, ...
   * 1. define mailbox; add dispatcher to config; attach to actor
   */
  class SupportTicketPriorityMailbox(settings: ActorSystem.Settings, config: Config)
    extends UnboundedPriorityMailbox(
      PriorityGenerator {
        case message: String if message.startsWith("[P0]") => 0
        case message: String if message.startsWith("[P1]") => 1
        case message: String if message.startsWith("[P2]") => 2
        case message: String if message.startsWith("[P3]") => 3
        case _ => 4
      }
    )

  val supportTicketActor = system.actorOf(Props[SimpleActor].withDispatcher("mailbox-dispatcher"))
  supportTicketActor ! "[P3] 3 ticket"
  supportTicketActor ! "[P0] urgent"
  supportTicketActor ! "[P1] one ticket"
  // after which time can i send another message and be prioritised accordingly - unknown and can't be configured

  /**
   * Use case 2 - control aware mailbox - UnboundedControlAwareMailbox;
   * 1 - mark a message as a control message
   * 2 - configure who gets the mailbox (dispatcher or actor)
   */
  case object ManagementTicket extends ControlMessage
  val controlAwareActor = system.actorOf(Props[SimpleActor].withMailbox("control-mailbox"))

  controlAwareActor ! "[P0] 00 "
  controlAwareActor ! "[P0] 01 "
  controlAwareActor ! "[P1] 10 "
  controlAwareActor ! ManagementTicket

  val controlAwareActorFromConfig = system.actorOf(Props[SimpleActor], "control-aware-actor")
  controlAwareActorFromConfig ! "[P0] 01 "
  controlAwareActorFromConfig ! "[P1] 10 "
  controlAwareActorFromConfig ! ManagementTicket







}
