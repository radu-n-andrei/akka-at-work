package akka.actors.patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Success

class AskSpec extends TestKit(ActorSystem("ask-spec")) with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  import AskSpec._

  "an authenticator" should {
    authenticatorTestSuite(Props[UserAuthActor])
  }

  "a piped authenticator" should {
    authenticatorTestSuite(Props[PipedAuthManager])
  }

  def authenticatorTestSuite(props: Props) = {
    "fail to authenticate a non registered user" in {
      val authManager = system.actorOf(props)
      authManager ! AuthUser("Radu", "shouldntwork")
      expectMsg(AuthFailed(404))
    }

    "fail to authenticate a user with a wrong pass" in {
      val authManager = system.actorOf(props)
      authManager ! RegisterUser("Radu", "pass01")
      authManager ! AuthUser("Radu", "shouldntwork")
      expectMsg(AuthFailed(401))
    }

    "successfully authenticate a user with a correct pass" in {
      val authManager = system.actorOf(props)
      authManager ! RegisterUser("Radu", "pass01")
      authManager ! AuthUser("Radu", "pass01")
      expectMsg(AuthSuccess)
    }
  }

}

object AskSpec {

  case class Read(key: String)

  case class Write(key: String, value: String)

  class KVActor extends Actor with ActorLogging {

    override def receive: Receive = online(Map.empty[String, String])

    def online(kv: Map[String, String]): Receive = {
      case Read(key) => log.info(s"Trying to read the value at $key")
        sender() ! kv.get(key)
      case Write(k, v) => log.info(s"Tring to write at key $k")
        context.become(online(kv + (k -> v)))
    }
  }

  case class RegisterUser(username: String, pass: String)

  case class AuthUser(username: String, pass: String)

  trait AuthResponse

  case class AuthFailed(code: Int) extends AuthResponse

  case object AuthSuccess extends AuthResponse

  class UserAuthActor extends Actor with ActorLogging {

    protected val kvActor = context.actorOf(Props[KVActor])
    implicit val timeout: Timeout = Timeout(1 second)
    implicit val executionContext: ExecutionContext = context.dispatcher

    override def receive: Receive = {
      case RegisterUser(u, p) => kvActor ! Write(u, p)
      case AuthUser(u, p) =>
        handleAuth(u, p)
    }

    def handleAuth(u: String, p: String) = {
      val originalSender = sender()
      val future = kvActor ? Read(u)
      future.onComplete {
        // avoid closing over the actor instance or mutable state
        case Success(None) => originalSender ! AuthFailed(404)
        case Success(Some(pass)) => if (p == pass) originalSender ! AuthSuccess else originalSender ! AuthFailed(401)
        case _ => originalSender ! AuthFailed(500)
      }
    }
  }

  class PipedAuthManager extends UserAuthActor {
    override def handleAuth(u: String, p: String): Unit = {
      val future = kvActor ? Read(u)
      val passFuture = future.mapTo[Option[String]]
      val responseFuture: Future[AuthResponse] = passFuture.map {
        case None => AuthFailed(404)
        case Some(pass) => if (pass == p) AuthSuccess else AuthFailed(401)
      }
      //when the future completes send the response to the actorRef
      responseFuture.pipeTo(sender())
    }
  }

}
