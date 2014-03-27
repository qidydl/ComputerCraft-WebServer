ComputerCraft-WebServer
=======================

qid's highly theoretical addon for <a href="http://www.computercraft.info/">ComputerCraft</a>, which is itself a mod for
<a href="https://minecraft.net/">Minecraft</a>.

The purpose of this mod is to allow a ComputerCraft computer to receive and respond to HTTP requests, allowing it to
act as a real web server. It's probably not going to be very fast and won't be able to handle a lot of requests, but I
consider this to be more of a technical experiment than anything truly useful.

In order to make this idea slightly more feasible, it will actually include a second component, a front-end web proxy.
I haven't started work on that yet, but it will likely be a Python or Ruby on Rails application. The proxy's purpose is
to handle the full HTTP protocol and transform it down into a simplified protocol that CC-WebServer will speak. The idea
is to get as much of the work as I can out of the Minecraft process and into the proxy, which should help performance.
I also plan to implement a couple of useful features in the proxy, such as Markdown support, so that the Lua code
running in ComputerCraft doesn't have to produce full HTML.