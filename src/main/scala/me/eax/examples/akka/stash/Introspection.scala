package me.eax.examples.akka.stash

import akka.actor._
import akka.pattern.ask

import scala.concurrent._
import scala.collection.immutable._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

case object Introspection extends AskHelper {

  case class QueueEntry(msg: Any, sender: ActorRef)
  case class ActorState(initDone: Boolean, queue: Queue[QueueEntry], currentMessage: Option[Any])

  private case object GetActorState
  private case object ManualRestart
  private case object ManualStop

  def getActorState(ref: ActorRef): Future[ActorState] = {
    (ref ? GetActorState).mapTo[ActorState]
  }

  def manualRestart(ref: ActorRef): Future[Unit] = {
    (ref ? ManualRestart).mapTo[Unit]
  }

  def manualStop(ref: ActorRef): Future[Unit] = {
    (ref ? ManualStop).mapTo[Unit]
  }

  // TODO: getRunningActors /* только с интроспецией - свой реестр */
}

trait Introspection extends Actor with ActorLogging {
  import Introspection._

  private case object Ready
  private case object Initializing

  private var queue = Queue[QueueEntry]()

  private var currentMessage: Option[Any] = Some(Initializing)

  protected def init: Future[Unit] = Future.successful({})

  protected def initialized: PartialFunction[Any, Future[Any]]

  protected def currentQueueSize(): Int = {
    queue.length
  }

  override def preStart(): Unit = {
    wait(Initializing, init)
  }

  override def receive: Receive = {
    case GetActorState => sender ! currentState()
    // see http://doc.akka.io/docs/akka/snapshot/scala/fault-tolerance.html#Default_Supervisor_Strategy
    case ManualRestart => sender ! {}; throw new RuntimeException("Manual restart")
    case ManualStop => sender ! {}; self ! Kill
  }

  private def waiting: Receive = receive orElse {
    case Ready =>
      currentMessage = None

      if(queue.isEmpty) {
        context.become(ready, discardOld = true)
      } else {
        val (stashedMsg, newQueue) = queue.dequeue
        queue = newQueue
        self.tell(stashedMsg, stashedMsg.sender)
      }

    case QueueEntry(msg, _) =>
      wait(msg, initialized(msg))

    case msg if initialized.isDefinedAt(msg) =>
      queue = queue.enqueue(QueueEntry(msg, sender()))
  }

  private def ready: Receive = receive orElse {
    case msg if initialized.isDefinedAt(msg) =>
      log.debug(s"processing msg: $msg")
      wait(msg, initialized(msg))
  }

  private def wait(msg: Any, fLazyWaitFor: => Future[Any]): Unit = {
    currentMessage = Some(msg)

    val selfRef = self

    val fWaitFor = Try(fLazyWaitFor).recover{ case err => Future.failed(err)}.get
    // ^ keep this line, init and initialized methods can throw exceptions

    context.become(waiting, discardOld = true)
    fWaitFor.onSuccess{ case _ => selfRef ! Ready }
    fWaitFor.onFailure{ case _ => selfRef ! PoisonPill }
  }

  private def currentState(): ActorState = {
    val initDone = !currentMessage.contains(Initializing)
    ActorState(initDone, queue, currentMessage)
  }

  implicit class ActorRefWithTellFAndForwardF(ref: ActorRef) {
    def tellF(message: Any)(implicit sender: ActorRef = Actor.noSender): Future[Unit] = {
      ref.!(message)(sender)
      Future.successful({})
    }

    def forwardF(message: Any)(implicit context: ActorContext): Future[Unit] = {
      ref.tell(message, context.sender())
      Future.successful({})
    }
  }
}
