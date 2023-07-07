package akka.actors

import ChangingBehaviour.Kid.HAPPY
import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChangingBehaviour extends App {

  class Kid extends Actor {
    import Kid._
    import Mom._
    var state = HAPPY
    override def receive: Receive = {
      case Food(VEG) => state = SAD
      case Food(CHOC) => state = HAPPY
      case Ask => if(state == HAPPY) sender() ! KAccept else sender() ! KReject
    }
  }

  object Kid {
    case object KAccept
    case object KReject
    val HAPPY = "happy"
    val SAD = "sad"

  }

  class StatelessKid extends Actor {
    import Kid._
    import Mom._
    override def receive: Receive = happyReceive

    def happyReceive: Receive = {
      case Food(VEG) => context.become(sadReceive, false)// change the handler
      case Food(CHOC) => ()// do noth
      case Ask => sender() ! KAccept
    }
    def sadReceive: Receive = {
      case Food(CHOC) => context.unbecome
      case Food(VEG) => context.become(sadReceive, false)
      case Ask => sender() ! KReject
    }
  }

  class Mom extends Actor {
    import Mom._
    import Kid._
    override def receive: Receive = {
      case MomStart(kr) => {
        kr ! Food(VEG)
        kr ! Ask
        kr ! Food(VEG)
        kr ! Ask
        kr ! Food(CHOC)
        kr ! Ask
        kr ! Food(CHOC)
        kr ! Ask
      }
      case KAccept => println("Yey he said yes")
      case KReject => println("You little shit!")
    }
  }

  object Mom {
    case class Food(kind: String)
    case object Ask

    val VEG = "veggie"
    val CHOC = "chocolate"
  }

  case class MomStart(kidRef: ActorRef)

  val actorSys = ActorSystem("changing-b")
  val kidRef = actorSys.actorOf(Props[Kid], "kid-actor")
  val statKidRef = actorSys.actorOf(Props[StatelessKid], "stat-kid-actor")
  val momRef = actorSys.actorOf(Props[Mom], "mom-actor")

  momRef ! MomStart(statKidRef)
}
