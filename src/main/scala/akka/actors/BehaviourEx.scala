package akka.actors

import BehaviourEx.Citizen.Vote
import BehaviourEx.StatelessCount.{Decrement, Increment, Print}
import BehaviourEx.VoteAggregator.VoteCountReq
import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object BehaviourEx extends App {

  /**
   * 1. recreate the counter actor w/ context.become and no mutable state. hint: handlers take params
   * 2. simplified voting system. 2 kinds of actors: citizen, vote aggregator. case class vote contains candidate: string
   * vote -> citizen (becomes hasVoted)
   * voteagg -> citizen (who have you voted with).
   * vote agg gets a list of citizen ref and sends a status? to them. they reply with a vote status
   * 4 citizens that vote and we aggregate their votes and print them as a map
   */

  // 1 - stateless counter

  class StatelessCount extends Actor {

    import StatelessCount._

    override def receive: Receive = counter(0)

    def counter(currentValue: Int): Receive = {
      case Increment => context.become(counter(currentValue + 1))
      case Decrement => context.become(counter(currentValue - 1))
      case Print => println(s"My counter is currently at: $currentValue")
    }
  }

  object StatelessCount {
    case object Increment

    case object Decrement

    case object Print
  }

  val sys = ActorSystem("behaviour-exe")
  val counter = sys.actorOf(Props[StatelessCount], "counter-actor-1")
  counter ! Increment
  counter ! Increment
  counter ! Decrement
  counter ! Increment
  counter ! Decrement
  counter ! Increment
  counter ! Print

  // Voting system
  class Citizen extends Actor {

    import Citizen._

    override def receive: Receive = notVoted

    def notVoted: Receive = {
      case Vote(candidateName) => context.become(voted(candidateName))
      case VoteQuery => sender() ! VoteReply(None)
    }

    def voted(candidate: String): Receive = {
      case VoteQuery => sender() ! VoteReply(Some(candidate))
    }
  }

  object Citizen {
    case class Vote(candidate: String)

    case object VoteQuery

    case class VoteReply(candidate: Option[String])

  }

  class VoteAggregator extends Actor {

    import VoteAggregator.VoteCountReq
    import Citizen.{VoteQuery, VoteReply}

    override def receive: Receive = waitingForRequests

    def waitingForRequests: Receive = {
      case VoteCountReq(citizens) => {
        println(s"Requesting votes for ${citizens.size} citizens")
        citizens.foreach(_ ! VoteQuery)
        context.become(counting(Map(), citizens.size))
      }
      case _ => println("voting aggregator is confused")

    }

    def counting(tally: Map[String, Int], quorum: Int): Receive = {
      case VoteReply(Some(candidate)) => {
        val countStatus = tally.updatedWith(candidate)(n => n.map(_ + 1).orElse(Some(1)))
        if (countStatus.values.sum == quorum) {
          println("Stop the count: ")
          countStatus.foreach(println)
          println("-----------------")
          context.become(waitingForRequests)
        } else {
          context.become(counting(countStatus, quorum))
        }
      }
      case VoteReply(None) => sender() ! VoteQuery
      case VoteCountReq(_) => println("Still counting...")
      case _ => println("why am i still counting")
    }
  }

  object VoteAggregator {
    case class VoteCountReq(voters: Set[ActorRef])
  }


  val jack = sys.actorOf(Props[Citizen], "jack-voter")
  val john = sys.actorOf(Props[Citizen], "john-voter")
  val bill = sys.actorOf(Props[Citizen], "bill-voter")
  val bob = sys.actorOf(Props[Citizen], "bob-voter")

  val voteagg = sys.actorOf(Props[VoteAggregator], "vote-agg")

  jack ! Vote("Mao")
  bill ! Vote("Radu")
  bob ! Vote("Mitsi")

  voteagg ! VoteCountReq(Set(jack, john, bill, bob))
  Thread.sleep(1000)
  john ! Vote("Mao")
}


