package me.eax.examples.akka.stash

import akka.actor._
import akka.pattern.{ask, pipe}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object TestActor {
  case class HeavyWork(taskNumber: Int)

  case class AskExt(ref: ActorRef) extends AskHelper {
    def heavyWork(taskNumber: Int): Future[Double] = {
      (ref ? HeavyWork(taskNumber)).mapTo[Double]
    }
  }
}

class TestActor extends Actor with Introspection with ActorLogging {
  import TestActor._

  override protected def init: Future[Unit] = {
    Future successful {}
  }

  override protected def initialized = {
    case r: HeavyWork =>
      log.debug(s"Processing $r, current queue size: ${currentQueueSize()}")
      heavyWork() pipeTo sender
  }

  private def heavyWork(): Future[Double] = {
    Future {
      log.debug("Entering heavyWork()")
      var cnt: Double = 1
      for(i <- 1L to 1000000L) { cnt = cnt*1.000001 }
      log.debug("Leaving heavyWork()")
      cnt
    }
  }
}
