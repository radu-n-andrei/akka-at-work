package akka.actors

import ChildActors.CreditCard.{AttachToAccount, CheckStatus}
import ChildActors.NaiveBankAccount.InitialiseAccount
import ChildActors.Parent.{CreateChild, TellChild}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChildActors extends App {

  class Parent extends Actor {

    import Parent._

    override def receive: Receive = {
      case CreateChild(name) =>
        println(s"[parent] creating a new child named: $name")
        context.become(withChild(context.actorOf(Props[Child], name)))
      case TellChild(_) => println("[parent] no children created yet")

    }

    def withChild(child: ActorRef): Receive = {
      case TellChild(m) => child ! m
    }
  }

  object Parent {
    case class CreateChild(name: String)

    case class TellChild(msg: String)
  }

  class Child extends Actor {
    override def receive: Receive = {
      case s => println(s"[${self.path}]Received a message: ${s.toString}")
    }
  }

  val sys = ActorSystem("child-demo")
  val parent = sys.actorOf(Props[Parent], "parent1")
  parent ! CreateChild("Mao")
  parent ! TellChild("paspaspaspas")

  // actor hierarchies can be created through akka
  // there are 3 Guardian actors: /system, /user, / <- root guardian; manages system&user

  // actor selection
  val childSelection = sys.actorSelection("user/parent1/Mao")
  childSelection ! "mautiii"

  // some danger: NEVER PASS MUTABLE ACTOR STATE OR this TO CHILD ACTORS => BREAKS ENCAPSULATION
  class NaiveBankAccount extends Actor {
    import NaiveBankAccount._
    import CreditCard._
    var balance = 0
    override def receive: Receive = {
      case InitialiseAccount => val ccRef = context.actorOf(Props[CreditCard], "cc")
        ccRef ! AttachToAccount(this)
      case Deposit(f) => depositFunds(f)
      case Withdraw(f) => withdrawFunds(f)
      case _ => println(s"[naive ba] $balance")
    }

    def depositFunds(f: Int): Unit = balance += f
    def withdrawFunds(f: Int): Unit = balance -= f
  }

  object NaiveBankAccount {
    case class Deposit(amt: Int)
    case class Withdraw(amt: Int)
    case object InitialiseAccount
  }

  class CreditCard extends Actor {
    override def receive: Receive = {
      case AttachToAccount(acc) => context.become(attached(acc))
    }

    def attached(account: NaiveBankAccount): Receive = {
      case CheckStatus => println(s"[${self.path}]: Request has been processed")
        account.withdrawFunds(1)
    }
  }

  object CreditCard {
    case class AttachToAccount(acc: NaiveBankAccount) // !!!!! actor instead of a ref
    case object CheckStatus
  }

  val bankAccount = sys.actorOf(Props[NaiveBankAccount], "stupid-ba")
  bankAccount ! InitialiseAccount
  Thread.sleep(100)
  val cc = sys.actorSelection("/user/stupid-ba/cc")
  cc ! CheckStatus
  Thread.sleep(100)
  bankAccount ! "money?"

}
