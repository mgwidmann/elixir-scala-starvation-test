Starvation
==========

My personal interests lies with Elixir, but recently work has pushed our team towards Scala (and away from Ruby wtf?).

So I thought I'd check into potential actor starvation and how they work between the two actor based systems. The test is to make an actor come alive, +1 a counter, send the new count value to a newly spawned actor and have the current actor then sleep waiting. To make it scale terribly, the maximum count value will be used as the sleep time.

## So heres the code, Elixir first of course!

```elixir
defmodule Starvation do

  def count(max, start \\ now) when is_integer(max) do
    spawn fn ->
      receive do
        i when max >= i ->
          IO.puts "#{inspect self} Received #{i}"
          actor = count(max, start)
          send(actor, i + 1)
          IO.puts "#{inspect self} Sleeping for #{max} ms"
          :timer.sleep(max)
          IO.puts "#{inspect self} Actor exiting..."
        i ->
          done = now
          IO.puts "Done at #{i} in just #{(done - start)} s"
      end
    end
  end

  def now, do: :calendar.datetime_to_gregorian_seconds(:calendar.universal_time)

end
```

Fire up iex, and start this thing off! We increase the number of processes allowed so we don't crash... 10 million is overkill but it should do!

```bash
$ iex --erl "+P 10000000" -S mix
Erlang/OTP 17 [erts-6.1] [source] [64-bit] [smp:8:8] [async-threads:10] [hipe] [kernel-poll:false] [dtrace]

Compiled lib/starvation.ex
Generated starvation.app
Interactive Elixir (1.0.0) - press Ctrl+C to exit (type h() ENTER for help)
iex(1)> counter = Starvation.count(1_000_000)
#PID<0.88.0>
iex(2)> send(counter, 0)
#PID<0.88.0> Received 0
0
#PID<0.90.0> Received 1
#PID<0.88.0> Sleeping for 1000000 ms
#PID<0.90.0> Sleeping for 1000000 ms
#PID<0.91.0> Received 2
#PID<0.91.0> Sleeping for 1000000 ms
... Removed for brevity ...
#PID<0.1245.61> Received 999999
#PID<0.1245.61> Sleeping for 1000000 ms
#PID<0.1246.61> Received 1000000
#PID<0.1246.61> Sleeping for 1000000 ms
Done at 1000001 in just 45 s
iex(3)>
```

Awesome! 45 seconds my iMac with 8 cores!

## Ok Scala, time to step up to the plate!

Basically the same thing, only Scala likes to use objects for actors, so its done slightly differently. (Note: I'm still new to Scala, so if anyone sees any improvements please let me know, I know this is probably the wrong place to ask but just sayin)

```scala
import scala.actors.Actor
import scala.actors.Actor._

class Starvation(max: Int, start: Long) extends Actor {
  def act() = {
    receive {
      case i : Int if max >= i =>
        print(Thread.currentThread + ": Received " + i)
        val s = new Starvation(max, start)
        s.start
        s ! i + 1
        println(" ... starting new actor and sleeping for " + max + "ms")
        Thread.sleep(max)
        println(Thread.currentThread + " actor exiting...")
      case i : Int =>
        println(Thread.currentThread + ": Done in " (System.currentTimeMillis() - start) + "ms")
    }
  }
}
```

Ok, in the scala terminal:

```bash
$ scala
Welcome to Scala version 2.11.2 (Java HotSpot(TM) 64-Bit Server VM, Java 1.6.0_65).
Type in expressions to have them evaluated.
Type :help for more information.

scala> :paste
// Entering paste mode (ctrl-D to finish)

( Pasted above code in here)

val s = new Starvation(1000000, System.currentTimeMillis())
s.start
s ! 0

// Exiting paste mode, now interpreting.

warning: there was one deprecation warning; re-run with -deprecation for details
Thread[ForkJoinPool-2-worker-15,5,main]: Received 0 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-11,5,main]: Received 1 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-7,5,main]: Received 2 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-3,5,main]: Received 3import scala.actors.Actor
import scala.actors.Actor._
defined class Starvation
s: Starvation = Starvation@4641439f
 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-31,5,main]: Received 4 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-27,5,main]: Received 5 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-23,5,main]: Received 6 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-19,5,main]: Received 7 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-1,5,main]: Received 8 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-29,5,main]: Received 9 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-25,5,main]: Received 10 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-21,5,main]: Received 11 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-17,5,main]: Received 12 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-13,5,main]: Received 13 ... starting new actor and sleeping for 1000000ms

scala> Thread[ForkJoinPool-2-worker-9,5,main]: Received 14 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-5,5,main]: Received 15 ... starting new actor and sleeping for 1000000ms
```

Ok so it stopped at 15 and won't go any higher (which is conveniently number of cores * 2 since we started with 0), it just hangs for a while. All the threads are locked to the actors and everything is waiting on the sleeps (which is over 15 minutes, ugh).

Scala allows two different types of receiving messages, receive and react. The major difference is receive locks the actor to the thread because it maintains the current stack frame. So using react should theoretically fix the problem.

Using react:

```
Thread[ForkJoinPool-2-worker-29,5,main]: Received 0 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-15,5,main]: Received 1 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-25,5,main]: Received 2 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-21,5,main]: Received 3 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-17,5,main]: Received 4 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-9,5,main]: Received 5 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-13,5,main]: Received 6 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-1,5,main]: Received 7 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-19,5,main]: Received 8 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-5,5,main]: Received 9 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-11,5,main]: Received 10 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-7,5,main]: Received 11 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-3,5,main]: Received 12 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-31,5,main]: Received 13 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-27,5,main]: Received 14 ... starting new actor and sleeping for 1000000ms
Thread[ForkJoinPool-2-worker-23,5,main]: Received 15 ... starting new actor and sleeping for 1000000ms
```

Again... Stuck at 15. At this rate, with 1,000,000 actors to go through, processing at a rate of 16 at a time (assuming the processing time is actually nothing). 1 million milliseconds is 16.6667 minutes so:

#### (1,000,000 actors / 16 at a time) * 16.6667 minutes = 1,041,668.75 minutes which converts to 1.98055 years!


I'm aware this comparison may be a bit unfair in that it could be caused by the simple fact in how sleeping works between the two virtual machines. I'm unsure however what a good way to compare the two inserting an artificial delay in the actors when they for whatever reason decide to hog CPU time. With that said, I believe the difference being that the Erlang VM is aware of its actors and so can easily pause them at any point of execution regardless of what its doing (sleep or something else). The JVM and Scala by extension, must wait for the actor to terminate before moving onto another actor, which means once actor contention gets above number of cores * 2, throughput will drop dramatically (or maybe even completely stall). This seems like a surprisingly small limit and may be configurable as I haven't checked.
