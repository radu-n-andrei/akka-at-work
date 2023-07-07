package akka.actors

import akka.actor.{Actor, ActorSystem, Props}

object ActorsIntro extends App {

  // 1. starts with actor system - one for application instance
  val actorSys = ActorSystem("first-actor-sys")
  println(actorSys.name)

  // 2. create actors within the system
  class WordCountActor extends Actor {
    // internal data
    var totalWords = 0
    // receive handler
    override def receive: Actor.Receive = {
      case m: String =>
        totalWords += m.split(" ").length
        println(s"[word-counter] has received $m. TOTAL = $totalWords")
      case _ => println("[word-counter] can't understand message")
    }
  }

  // 3. instantiate actor - not through new, pass props[T] to the sys
  // uniquely identified by the name
  val wordCounter = actorSys.actorOf(Props[WordCountActor], "word-counter")
  val anotherWordCounter = actorSys.actorOf(Props[WordCountActor], "word-counter-2")

  // 4. communicate ASYNCHRONOUSLY
  wordCounter ! "i am weasel! akka is cool!"
  anotherWordCounter ! "i r baboon!"

  // !!! doesn't work. the actor trait has an internal val that will throw the exception if the context is not already set
  //val ww = new WordCountActor

  // you can pass arguments to the actor's apply withing the Props apply. that's legal but discouraged
  class PersonActor(name: String) extends Actor {
    override def receive: Receive = {
      case m: String => println(s"[$name] gets a $m")
    }
  }
  val personActor = actorSys.actorOf(Props(new PersonActor("MAO")), "mao-actor-1")

  personActor ! "belly rub"

  // proper way is w/ companian object
  object PersonActor {
    def props(name: String): Props = Props(new PersonActor(name))
  }
  val personActor2 = actorSys.actorOf(PersonActor.props("MITSI"), "mitsi-actor-1")

  personActor2 ! "kiss"
}
