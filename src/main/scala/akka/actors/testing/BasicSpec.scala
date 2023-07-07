package akka.actors.testing

import BasicSpec.{BlackHole, LabTestActor, SimpleActor}
import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.language.postfixOps
import scala.concurrent.duration._
import scala.util.Random

class BasicSpec extends TestKit(ActorSystem("basic-spec")) with ImplicitSender with WordSpecLike with BeforeAndAfterAll with Matchers {

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "a  SimpleActor" should {
    "reply with the same message" in {
      val act = system.actorOf(Props[SimpleActor])
      val message = "yo"
      act ! message
      expectMsg(message)
    }
  }

  "a BlackHole Actor" should {
    "reply with a message" in {
      val bhActor = system.actorOf(Props[BlackHole])
      bhActor ! "failure"
      expectNoMessage(1 seconds)
    }
  }

  "a lab test actor" should {
    val ltActor = system.actorOf(Props[LabTestActor])
    "turn a string into uppercase" in {
      ltActor ! "iloveakka"
      val message = expectMsgType[String]
      message shouldBe "ILOVEAKKA"
    }

    "reply to a greeting" in {
      ltActor ! "Greetings"
      expectMsgAnyOf("Hi", "Hello")
    }

    "reply w/ 2 techs" in {
      ltActor ! "fav"
      //expectMsgAllOf("Scala", "akka")
      // for more complicated assertions
      //val messages = receiveN(2)

      //fancier
      expectMsgPF() {
        case "Scala" => println("nice")
        case "akka" => ???
      }
    }
  }
}

object BasicSpec {
  // store all the values to be used in the test
  class SimpleActor extends Actor {
    override def receive: Receive = {
      case m => sender() ! m
    }
  }

  class BlackHole extends Actor {
    override def receive: Receive = Actor.emptyBehavior
  }

  class LabTestActor extends Actor {

    override def receive: Receive = {
      case "Greetings" => val reply = if (Random.nextBoolean()) "Hi" else "Hello"
        sender() ! reply
      case "fav" => sender() ! "Scala"
        sender() ! "akka"
      case m: String => sender() ! m.toUpperCase
    }
  }
}
