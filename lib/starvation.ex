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
