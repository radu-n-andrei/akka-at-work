package akka.actors.patterns

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, FSM, Props, Timers}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration._
import scala.language.postfixOps

class FSMSpec extends TestKit(ActorSystem("fsm-spec")) with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  import FSMSpec._

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }


  def runTest(actor: Props) {
    s"a vending machine of ${actor.clazz.toString}" should {
      "error when not initialised" in {
        val vm = system.actorOf(actor)
        vm ! RequestProduct("snickers")
        expectMsg(VendingError("[NOT INITIALISED]"))
      }

      "report a product not available" in {
        val vm = system.actorOf(actor)
        vm ! Initialise(Map("snickers" -> 3), Map("snickers" -> 5))
        vm ! RequestProduct("mars")
        expectMsg(VendingError("[PRODUCT NOT FOUND]"))
      }

      "timeout if money not received" in {
        val vm = system.actorOf(actor)
        vm ! Initialise(Map("snickers" -> 3), Map("snickers" -> 5))
        vm ! RequestProduct("snickers")
        expectMsg(Instruction("PLEASE INSERT: 5 dollars"))
        within(5.1 seconds) {
          expectMsg(VendingError("[TIMEOUT]"))
        }
      }

      "timeout if some money received" in {
        val vm = system.actorOf(actor)
        vm ! Initialise(Map("snickers" -> 3), Map("snickers" -> 5))
        vm ! RequestProduct("snickers")
        expectMsg(Instruction("PLEASE INSERT: 5 dollars"))
        within(3 seconds) {
          vm ! ReceiveMoney(3)
        }
        expectMsg(Instruction("PLEASE INSERT: 2 dollars"))
        within(5.1 seconds) {
          expectMsg(VendingError("[TIMEOUT]"))
          expectMsg(GiveBackChange(3))
        }
      }

      "sell if money received in several parts" in {
        val vm = system.actorOf(actor)
        vm ! Initialise(Map("snickers" -> 3), Map("snickers" -> 5))
        vm ! RequestProduct("snickers")
        expectMsg(Instruction("PLEASE INSERT: 5 dollars"))
        vm ! ReceiveMoney(3)
        expectMsg(Instruction("PLEASE INSERT: 2 dollars"))
        vm ! ReceiveMoney(2)
        expectMsg(Deliver("snickers"))
      }

      "sell and give back change" in {
        val vm = system.actorOf(actor)
        vm ! Initialise(Map("snickers" -> 3), Map("snickers" -> 5))
        vm ! RequestProduct("snickers")
        expectMsg(Instruction("PLEASE INSERT: 5 dollars"))
        vm ! ReceiveMoney(3)
        expectMsg(Instruction("PLEASE INSERT: 2 dollars"))
        vm ! ReceiveMoney(3)
        expectMsg(Deliver("snickers"))
        expectMsg(GiveBackChange(1))
      }
    }

  }

  runTest(Props[VendingMachine])
  runTest(Props[VendingMachineFSM])

}

object FSMSpec {

  /*
    Vending machine
   */

  case class Initialise(inventory: Map[String, Int], prices: Map[String, Int])

  case class RequestProduct(product: String)

  case class Instruction(instruction: String)

  case class ReceiveMoney(amount: Int)

  case class Deliver(product: String)

  case class GiveBackChange(amount: Int)

  case class VendingError(message: String)

  case object ReceiveMoneyTimeout

  case object TimerKey

  class VendingMachine extends Actor with ActorLogging with Timers {
    override def receive: Receive = idle

    def idle: Receive = {
      case Initialise(inventory, prices) => context.become(operational(inventory, prices))
      case _ => sender() ! VendingError("[NOT INITIALISED]")
    }

    def operational(inventory: Map[String, Int], prices: Map[String, Int]): Receive = {
      case RequestProduct(product) =>
        inventory.get(product) match {
          case None => sender() ! VendingError("[PRODUCT NOT FOUND]")
          case Some(_) =>
            sender() ! Instruction(s"PLEASE INSERT: ${prices(product)} dollars")
            timers.startSingleTimer(TimerKey, ReceiveMoneyTimeout, 5 seconds)
            context.become(waitForMoney(inventory, prices, product, 0, sender()))
        }
      case _ => sender() ! VendingError("[REQUEST A PRODUCT]")
    }

    def waitForMoney(inventory: Map[String, Int],
                     prices: Map[String, Int],
                     product: String,
                     money: Int,
                     requester: ActorRef): Receive = {
      case ReceiveMoneyTimeout =>
        requester ! VendingError("[TIMEOUT]")
        if (money > 0) requester ! GiveBackChange(money)
        context.become(operational(inventory, prices))
      case ReceiveMoney(amount) =>
        timers.cancel(TimerKey)
        val price = prices(product)
        if (money + amount >= price) {
          requester ! Deliver(product)
          if (money + amount - price > 0) requester ! GiveBackChange(money + amount - price)
          val newStock = inventory(product) - 1
          val newInventory = inventory + (product -> newStock)
          context.become(operational(newInventory, prices))
        } else {
          requester ! Instruction(s"PLEASE INSERT: ${price - money - amount} dollars")
          timers.startSingleTimer(TimerKey, ReceiveMoneyTimeout, 5 seconds)
          context.become(waitForMoney(inventory, prices, product, money + amount, requester))
        }
    }
  }

  // step 1 - define the states and the data
  trait VendingState

  case object Idle extends VendingState

  case object Operational extends VendingState

  case object Pending extends VendingState // wait for money

  trait VendingData

  case object Unitialised extends VendingData

  case class Initialised(inventory: Map[String, Int], prices: Map[String, Int]) extends VendingData

  case class WaitForMoney(inventory: Map[String, Int],
                          prices: Map[String, Int],
                          product: String,
                          money: Int,
                          requester: ActorRef) extends VendingData


  class VendingMachineFSM extends FSM[VendingState, VendingData] {
    // we don't have a receive handler
    // an event is triggered with the message and the data on hold
    startWith(Idle, Unitialised)

    when(Idle) {
      case Event(Initialise(inventory, prices), Unitialised) => goto(Operational) using Initialised(inventory, prices)
      case _ =>
        sender() ! VendingError("[NOT INITIALISED]")
        stay()
    }

    when(Operational) {
      case Event(RequestProduct(product), Initialised(i, p)) =>
        i.get(product) match {
          case None =>
            sender() ! VendingError("[PRODUCT NOT FOUND]")
            stay()
          case Some(_) =>
            sender() ! Instruction(s"PLEASE INSERT: ${p(product)} dollars")
            goto(Pending) using WaitForMoney(i, p, product, 0, sender())
        }
    }
    when(Pending, stateTimeout = 5 seconds) {
      case Event(StateTimeout, WaitForMoney(i, p, prod, mon, ref)) =>
        ref ! VendingError("[TIMEOUT]")
        if (mon > 0) ref ! GiveBackChange(mon)
        goto(Operational) using Initialised(i, p)
      case Event(ReceiveMoney(amount), WaitForMoney(i, p, prod, mon, ref))
      =>
        val price = p(prod)
        if (mon + amount >= price) {
          ref ! Deliver(prod)
          if (mon + amount - price > 0) ref ! GiveBackChange(mon + amount - price)
          val newStock = i(prod) - 1
          val newInventory = i + (prod -> newStock)
          goto(Operational) using Initialised(newInventory, p)
        } else {
          ref ! Instruction(s"PLEASE INSERT: ${price - mon - amount} dollars")
          goto(Pending) using WaitForMoney(i, p, prod, mon + amount, ref)
        }
    }

    whenUnhandled {
      case Event(_, _) =>
        sender() ! VendingError("404")
        stay()
    }

    onTransition {
      case stateA -> stateB => log.info(s"Transitioning from $stateA to $stateB")
    }

    initialize()
  }


}
