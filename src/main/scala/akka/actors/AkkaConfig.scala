package akka.actors

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object AkkaConfig extends App {

  class Logs extends Actor with ActorLogging {
    override def receive: Receive = {
      case m => log.info(m.toString)
    }
  }

  val configStr =
    """
      |akka {
      |  loglevel = ERROR
      |}
      |""".stripMargin

  // 1. through string
  val config = ConfigFactory.parseString(configStr)
  val sys = ActorSystem("config-demo", ConfigFactory.load(config))
  val l = sys.actorOf(Props[Logs], "logger-1")
  l ! "hakkuna"

  // 2. through default file
  val sys2 = ActorSystem("config-wfile-demo")
  val l2 = sys2.actorOf(Props[Logs], "logger-2")
  l2 ! "matata"

  // 3. multilevel config in config file
  val mySpecialConfig = ConfigFactory.load().getConfig("mySpecialConfig")
  val sys3 = ActorSystem("layered-config-demo", mySpecialConfig)
  val specActor = sys3.actorOf(Props[Logs], "logger-3")
  specActor ! "this is special"

  // 4. config in a separate folder
  val separateConfig = ConfigFactory.load("otherfolder/special.conf")
  val sys4 = ActorSystem("separate-config-demo", separateConfig)
  val separateActor = sys4.actorOf(Props[Logs], "logger-4")
  separateActor ! "this is special"

  // extra. find specific config items from config
  println(s"separate log leve ${separateConfig.getString("akka.logLevel")}")

  // file formats: json, properties
}
