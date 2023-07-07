package akka.actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ActorCaps extends App {

  class SimpleActor extends Actor {
    // actors have info about the context and themselves
    // context.self == this (self)

    // anything can be sent as long as the type is immutable and serializable
    override def receive: Receive = {
      case "Hi!" => context.sender() ! "hello to you to!" // replying to a message
      case m: String => println(s"[${self.path}] I have received $m from ${sender()}")
      case i: Int => println(s"[simple-actor] I can count to $i")
      case SpecialMessage(sm) => println(s"[simple-actor] Unwrapping the special message. I got $sm")
      case SelfMessage(sm) => println(s"[simple-actor] I need to send this again, one moment")
        self ! sm
      case SayHiTo(a) => println(s"[simple-actor] I'll say hi to someone")
        a ! "Hi!"
      case Fwd(m, ref) => ref forward m+"S" // the original sender is kept, so if A ! B forward C, C will see A as sender
    }
  }

  val actorSys = ActorSystem("actor-caps")

  val simpleActor = actorSys.actorOf(Props[SimpleActor],"simple-act-1")

  simpleActor ! "hello!"
  simpleActor ! 13

  case class SpecialMessage(contents: String)
  simpleActor ! SpecialMessage("extra stuff")

  case class SelfMessage(content: String)
  simpleActor ! SelfMessage("send again")

  // actors can reply to messages
  val alice = actorSys.actorOf(Props[SimpleActor], "actor-alice")
  val bob = actorSys.actorOf(Props[SimpleActor], "actor-bob")

  // this is so cool. so, the ref can be used to send a message to some other actor and context.sender can be used to reply to the sender yet again
  case class SayHiTo(ref: ActorRef)
  alice ! SayHiTo(bob)

  // actor will reply to the sender. this will reach deadLetters
  alice ! "Hi!"

  // actors can fwd messages
  case class Fwd(content: String, ref: ActorRef)
  // bob will see the actor that send alice the Fwd as the sender, deadLetters in our case
  alice ! Fwd("Hi", bob)

}
