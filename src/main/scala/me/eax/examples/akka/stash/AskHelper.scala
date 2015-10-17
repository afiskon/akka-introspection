package me.eax.examples.akka.stash

import akka.util.Timeout
import scala.concurrent.duration._

trait AskHelper {
  implicit val timeout = Timeout(10.seconds)
}
