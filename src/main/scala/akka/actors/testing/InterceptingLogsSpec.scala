package akka.actors.testing

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class InterceptingLogsSpec extends TestKit(ActorSystem("interception",
  ConfigFactory.load().getConfig("interception")))
  with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  import InterceptingLogsSpec._

  "checkout flow" should {
    "correctly log the dispatch of an order" in {
      EventFilter.info(pattern = s"Order id [0-9]+ has been dispatched", occurrences = 1) intercept {
        val checkoutRef = system.actorOf(Props[CheckoutActor])
        checkoutRef ! Checkout("item", "1234-5678")
      }
    }

    "be rude if the payment is denied" in {
      EventFilter[RuntimeException](occurrences = 1) intercept {
        val checkoutRef = system.actorOf(Props[CheckoutActor])
        checkoutRef ! Checkout("item", "0234-5678")
      }
    }
  }


}

object InterceptingLogsSpec {

  case class Checkout(item: String, creditCard: String)

  case class AuthorizeCard(str: String)

  case object PaymentAccepted

  case object PaymentDenied

  case class DispatchOrder(item: String)

  case object DispatchConfirmed

  class CheckoutActor extends Actor {
    // 2 child actors
    private val paymentManager = context.actorOf(Props[PaymentManager])
    private val fulfillmentManager = context.actorOf(Props[FulfillmentManager])

    override def receive: Receive = awaitingCheckout

    def awaitingCheckout: Receive = {
      case Checkout(item, cc) => paymentManager ! AuthorizeCard(cc)
        context.become(pendingPayment(item))
    }

    def pendingPayment(str: String): Receive = {
      case PaymentAccepted => fulfillmentManager ! DispatchOrder(str)
        context.become(pendingFulfilment(str))
      case PaymentDenied => throw new RuntimeException("No mo' money")
    }

    def pendingFulfilment(item: String): Receive = {
      case DispatchConfirmed => context.become(awaitingCheckout)
    }
  }

  class PaymentManager extends Actor {
    override def receive: Receive = {
      case AuthorizeCard(c) => if (c.startsWith("0")) sender() ! PaymentDenied else {
        Thread.sleep(2000)
        sender() ! PaymentAccepted
      }
    }
  }

  class FulfillmentManager extends Actor with ActorLogging {
    var orderId = 0

    override def receive: Receive = {
      case _: DispatchOrder =>
        log.info(s"[FM] Order id $orderId has been dispatched")
        orderId += 1
        sender() ! DispatchConfirmed
    }
  }
}
