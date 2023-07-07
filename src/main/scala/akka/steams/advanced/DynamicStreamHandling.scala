package akka.steams.advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitches}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Success

object DynamicStreamHandling extends App {
  implicit val system = ActorSystem("dynamic")
  implicit val materializer = ActorMaterializer()

  // 1. Kill Switch - flow that materializes to smth special
  val killSwitchFlow = KillSwitches.single[Int]

  val counter = Source(LazyList.from(1)).throttle(1, 1 second).log("counter")
  val ignoreSink = Sink.ignore

  /*val killSwitch = counter.viaMat(killSwitchFlow)(Keep.right).to(ignoreSink).run()

  system.scheduler.scheduleOnce(3 seconds) {
    killSwitch.shutdown()
  }(system.dispatcher)*/

  val anotherCounter = Source(LazyList.from(1)).throttle(2, 1 second).log("other-counter")
  val sharedKillSwitch = KillSwitches.shared("shared-switch")

  /*counter.via(sharedKillSwitch.flow[Int]).runWith(ignoreSink)
  anotherCounter.via(sharedKillSwitch.flow[Int]).runWith(ignoreSink)
  system.scheduler.scheduleOnce(3 seconds) {
    sharedKillSwitch.shutdown()
  }(system.dispatcher)*/

  // 2. Merge hub
  val dynamicMerge = MergeHub.source[Int]
  val materializedSink = dynamicMerge.to(Sink.foreach[Int](i => println(s"Dynamic: $i"))).run()

  //Source(1 to 10).runWith(materializedSink)
  //counter.runWith(materializedSink)

  // 3. Broadcast hub
  val dynamicBroadcast = BroadcastHub.sink[Int]
  val materializedSource = Source(1 to 100).runWith(dynamicBroadcast)

  //materializedSource.runWith(Sink.ignore)
  //materializedSource.runWith(Sink.foreach[Int](println))

  /**
   * Combine a mergeHub and a broadcastHub.
   * any uses? a publisher-subscriber component
   */
  val cDynamicMerge = MergeHub.source[String]
  val cDynamicBcast = BroadcastHub.sink[String]
  val (publisherPort, subscriberPort) = cDynamicMerge.toMat(cDynamicBcast)(Keep.both).run()
  subscriberPort.runWith(Sink.foreach(el => println(s"I received the element $el")))
  subscriberPort.map(st => st.length).runWith(Sink.foreach(n => println(s"I got a number: $n")))

  Source(List("Akka", "is", "amazing")).runWith(publisherPort)
  Source(List("I", "love", "scala")).runWith(publisherPort)
  Source.single("STREAMS").runWith(publisherPort)
}
