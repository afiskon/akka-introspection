package me.eax.examples.akka.stash

import akka.actor._

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object Main extends App {
  val system = ActorSystem("system")
  val testActorRef = system.actorOf(Props(new TestActor), "testActor1")
  val testActor = TestActor.AskExt(testActorRef)

  val reqSeq1 = {
    for(i <- 1 to 10) yield testActor.heavyWork(i)
  }

  Await.result(reqSeq1.head, Duration.Inf)

  val state = Await.result(Introspection.getActorState(testActorRef), Duration.Inf)
  println(
    ">>> " +
    s"initDone: ${state.initDone}, queue size: ${state.queue.size}, " +
    s"current message: ${state.currentMessage}, ref to path: ${testActorRef.path}"
  )

  val reqSeq2 = {
    for(i <- 11 to 20) yield testActor.heavyWork(i)
  }

  Await.result(Future.sequence(reqSeq1 ++ reqSeq2), Duration.Inf)

  println("Restarting actor")
  Introspection.manualRestart(testActorRef)

  Thread.sleep(1000)

  println("Stopping actor")
  Introspection.manualStop(testActorRef)

  Thread.sleep(1000)

  system.shutdown()
}
