package akka.steams

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}


object Intro extends App {

  implicit val actorSystem = ActorSystem("intro")
  implicit val materializer = ActorMaterializer()

  Source.single("hello, Steams!").to(Sink.foreach(println)).run()

}
