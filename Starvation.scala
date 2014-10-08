import scala.actors.Actor
import scala.actors.Actor._

class Starvation(max: Int, start: Long) extends Actor {
  def act() = {
    react {
      case i : Int if max >= i =>
        print(Thread.currentThread + ": Received " + i)
        val s = new Starvation(max, start)
        s.start
        s ! i + 1
        println(" ... starting new actor and sleeping for " + max + "ms")
        Thread.sleep(max)
        println(Thread.currentThread + " actor exiting...")
      case i : Int =>
        println(Thread.currentThread + ": Done in " + (System.currentTimeMillis() - start) + "ms")
    }
  }
}

val s = new Starvation(1000000, System.currentTimeMillis())
s.start
s ! 0
