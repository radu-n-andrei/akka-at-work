package akka.actors

import ActorExe.CounterActor.{Decrement, Increment, Print}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}


/**
 * 1. counter actor w/ increment, decrement & prints (has a var)
 * 2. bank account as an actor: receive msg to deposit & withdraw -> replies w/ a Succ/Fail + statement.Make it interact w/ another kind of actor
 * that sends stuff and interprets the response
 */

object ActorExe extends App {
  import ActorExe.BankAccount._
  class CounterActor extends Actor {
    var counter: Int = 0

    override def receive: Receive = {
      case Increment => counter += 1
      case Decrement => counter -= 1
      case Print => println(s"[counter] has amassed $counter")
    }
  }

  object CounterActor {
    case object Increment

    case object Decrement

    case object Print
  }

  val actorSys = ActorSystem("actor-sys")

  val counterRef = actorSys.actorOf(Props[CounterActor], "basic-counter")

  /*counterRef ! "increment"
  counterRef ! "decrement"
  counterRef ! "print"
  */

  class BankAccount(var balance: Int) extends Actor {

    import BankAccount._

    override def receive: Receive = {
      case Deposit(amt) =>
        if (amt <= 0) sender() ! TransactionFailure("DEPOSIT", "Illegal amount")
        else {
          balance += amt
          sender() ! TransactionSuccess("DEPOSIT")
        }
      case Withdraw(amt) =>
        if (balance < amt) sender() ! TransactionFailure("WITHDRAWAL", "Insufficient funds")
        else {
          if (amt < 0) sender() ! TransactionFailure("WITHDRAWAL", "Illegal amount")
          else {
            balance -= amt
            sender() ! TransactionSuccess("WITHDRAWAL")
          }
        }
      case Balance => sender() ! BalanceResponse(balance)
    }
  }

  object BankAccount {
    def props(balance: Int) = Props(new BankAccount(balance))

    case class Deposit(amount: Int)

    case class Withdraw(amount: Int)

    case object Balance

    case class TransactionSuccess(transactionType: String)

    case class TransactionFailure(transactionType: String, reason: String)

    case class BalanceResponse(amt: Int)
  }

  class InternetBankingClient(val account: ActorRef) extends Actor {

    import BankAccount._

    override def receive: Receive = {
      // send the commands to the bank account actor
      case d: Deposit => {
        account ! d
      }
      case w: Withdraw => {
        account ! w
      }
      case Balance => {
        account ! Balance
      }
      // process bank account responses
      case TransactionSuccess(t) => println(s"[Ibanker] $t was successful")
      case TransactionFailure(t, e) => println(s"[Ibanker] $t failed: $e")
      case BalanceResponse(amt) => println(s"[Ibanker] Balance = $amt")
    }
  }

  object InternetBankingClient {
    def props(account: ActorRef) = Props(new InternetBankingClient(account))
  }

  val bankAccountActor = actorSys.actorOf(BankAccount.props(5000), "bank-account-1")
  val internetClinetActor = actorSys.actorOf(InternetBankingClient.props(bankAccountActor), "internet-banker-1")

  internetClinetActor ! Balance
  internetClinetActor ! Deposit(2000)
  internetClinetActor ! Withdraw(5000)
  internetClinetActor ! Withdraw(2001)
  internetClinetActor ! Balance


}

