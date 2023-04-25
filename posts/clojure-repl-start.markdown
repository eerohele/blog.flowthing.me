---
title: Faster Clojure REPL startup
description: A totally rigorous and unbelievably methodical survey into making your Clojure REPL start faster.
date: 2022-04-21
---

## tl;dr

To make your Clojure [REPL](https://clojure.org/guides/repl/introduction) start fast (in about 1.5 seconds), in decreasing order of impact:

- Avoid requiring namespaces at the top level of your `user.clj` file; use `requiring-resolve` to defer loading instead
- [Compile your dependencies](https://clojure.org/guides/dev_startup_time)
- Use the [Clojure CLI](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools) instead of Leiningen
- Use a [socket server](https://clojure.org/reference/repl_and_main#_launching_a_socket_server) instead of nREPL

## Off to the races

In this article, I'll discuss some of the things you can do to make sure your Clojure REPL starts as fast as possible. I'll also mention how to load all the [libs](https://clojure.org/reference/libs) your app requires faster.

To whet your appetite, here's a before-and-after: in one of my work projects (clocking in at about 15,000 lines of Clojure), applying these changes decreased the REPL startup time from 23 seconds to 1.6 seconds. Additionally, it decreased the time it takes to load (`require`) all of the libs the app needs from 12 seconds to 5.4 seconds.

In this article, I'll discuss, in decreasing order of impact, some of the factors that affect your REPL startup time. I'll begin by discussing the effect your `user.clj` file has on the startup time. Then, I'll briefly discuss avoiding recompiling your dependencies every time you load them. Next, I'll investigate the impact of choosing between [Clojure CLI](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools) and [Leiningen](https://leiningen.org/). I'll also examine whether the [socket server](https://clojure.org/reference/repl_and_main#_launching_a_socket_server) built into Clojure starts any faster than [nREPL](https://nrepl.org/), the most popular Clojure network REPL.

## Kind of a weird artifact

When Clojure starts, it [loads the first file named `user.clj` it finds](https://github.com/clojure/clojure/blob/c01f10bd8c68bd16c657fe352b206573f9306168/src/jvm/clojure/lang/RT.java#L486) in the [classpath](https://clojure.org/reference/deps_and_cli#_basis_and_classpath) of your program. Even though it's not very well documented (as of writing this article), most Clojure developers know this.

Because Clojure automatically loads whatever you put in `user.clj`, many Clojure developers use it as a repository for development-time helper functions. Here's an example of a `user.clj` file that will look familiar to many Clojure developers. It is adapted [from the README of Component](https://github.com/stuartsierra/component/tree/929dc7b4dc473b19f1417a918d5707ea8a1c9f2e#reloading), a Clojure library for managing application state.

```clojure
(ns user
  (:require [com.stuartsierra.component :as component]
            [my.app :as app]))

(defonce system nil)

(defn init []
  (alter-var-root #'system
    (constantly (app/example-system {:host "dbhost.com" :port 123}))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system #(some-> % component/stop)))

(comment
  (init)
  (start)
  (stop)
  ,,,)
```

In this example, `my.app` is the entry point namespace of the application. A medium-size Clojure project can have hundreds of loaded libs.

When you require `my.app` in `user.clj`, whenever you run Clojure (via `clj` or `lein` or whatever), Clojure dutifully loads every lib your application needs to run. That can take a pretty long time. Here's me loading the entry point namespace of my work project:

```clojure
user=> (time (require 'app.core))
"Elapsed time: 12156.287987 msecs"
nil
user=> (count (loaded-libs))
297
```

That's 12 seconds to load \~300 libs.

The takeaway here is that if you require the entry point namespace for your application in your `user.clj`, you pay a hefty penalty every time you start a REPL. The same is true if you require any (transitively) large namespace. [clojure.core.async](https://github.com/clojure/core.async), in particular, is a usual suspect: requiring core.async alone takes 3.5 seconds on my machine.

Now that we know what the (biggest) factor affecting REPL startup time is, let's look at how to alleviate it.

## Deferring code loading

To avoid the startup time penalty requiring namespaces in `user.clj` incurs, we'll use a clojure.core function called [`requiring-resolve`](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/requiring-resolve). You can think of `requiring-resolve` as a function that lazily requires [vars](https://clojure.org/reference/vars) from namespaces that might or might not already be loaded. In `user.clj`, we can use it to avoid having Clojure load any libs when it starts.

Here's the Component example from earlier rewritten using `requiring-resolve`:

```clojure
(ns user)

(defonce system nil)

(defn init []
  (alter-var-root #'system
    (constantly ((requiring-resolve 'my.app/example-system)
                 {:host "dbhost.com" :port 123}))))

(defn start []
  (alter-var-root #'system
    (requiring-resolve 'com.stuartsierra.component/start)))

(defn stop []
  (alter-var-root #'system
    #(some-> % ((requiring-resolve 'com.stuartsierra.component/stop)))))

(comment
  (init)
  (start)
  (stop)
  ,,,)
```

Notice how there's no `:require` in the `ns` form. This might not be what you'd call pretty, but it does make a pretty big difference. Let's use [`hyperfine`](https://github.com/sharkdp/hyperfine) to find out just how big. Here's how long it takes to start a REPL for my work project *before* refactoring `user.clj` to use `requiring-resolve`:

```
λ hyperfine --warmup 3 "echo ':repl/quit' | clj -J-Dclojure.server.repl='{:port 5555 :accept clojure.core.server/repl}' -M:dev -r"
Benchmark 1: echo ':repl/quit' | clj -J-Dclojure.server.repl='{:port 5555 :accept clojure.core.server/repl}' -M:dev -r
  Time (mean ± σ):     23.142 s ±  2.761 s    [User: 54.177 s, System: 3.629 s]
  Range (min … max):   19.347 s … 27.199 s    10 runs
```

23 seconds. Here's the result of running the same benchmark *after* using `requiring-resolve` to remove all `:require`s from `user.clj`:

```text
λ hyperfine --warmup 3 "echo ':repl/quit' | clj -J-Dclojure.server.repl='{:port 5555 :accept clojure.core.server/repl}' -M:dev -r"
Benchmark 1: echo ':repl/quit' | clj -J-Dclojure.server.repl='{:port 5555 :accept clojure.core.server/repl}' -M:dev -r
  Time (mean ± σ):      1.611 s ±  0.042 s    [User: 2.296 s, System: 0.274 s]
  Range (min … max):    1.559 s …  1.703 s    10 runs
```

And so, we're down to 1.5 seconds.

Of course, you have to pay the cost of requiring all those libs eventually. By using `requiring-resolve`, you're simply deferring the payment: you'll pay it when you actually call one of the functions that calls `requiring-resolve` (like `start`, for example). It's up to you to decide whether the tradeoff is worth it. To me, it is: just because I want to run a REPL doesn't always mean I want to run the whole app, too.

Besides using `requiring-resolve` to avoid loading anything on startup, there's something else we can do to load libs faster: compile our dependencies. Let's see how.

## Avoiding dependency recompilation

Most Clojure libraries are distributed as packages of Clojure source files. That means that every time you load a lib, Clojure must first compile it to bytecode. Compilation accounts for maybe half of the time Clojure spends on loading libs.

To load libs faster, you can compile them beforehand, as proposed in [the official *Improving Development Startup Time* guide](https://clojure.org/guides/dev_startup_time). As a reminder, before making the changes proposed in the guide, it took 12 seconds to require the entry point namespace for my work project. Here's how long it takes after following the instructions in the guide:

```clojure
user=> (time (require 'app.core))
"Elapsed time: 5428.763059 msecs"
nil
```

Less than half of the original duration. Not too shabby.

Avoiding lib-loading and compilation on startup have the largest impact on REPL startup time by far, so it's totally reasonable to stop here. If you want to go as fast as possible, though, you might also want to consider using the Clojure CLI instead of Leiningen. That's what we'll look into next.

## Clojure CLI vs. Leiningen

First, let's use [the Clojure command-line tool](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools) (henceforth `clj`) to start (and immediately quit) a Clojure REPL. To make it a REPL you can connect your [editor](https://tutkain.flowthing.me) to, we'll use the `clojure.server` Java system property to also [launch a socket server](https://clojure.org/reference/repl_and_main#_launching_a_socket_server).

```text
# We use --warmup 3 to run the benchmark on a warm disk cache.
λ hyperfine --warmup 3 "echo ':repl/quit' | clj -J-Dclojure.server.repl='{:port 5555 :accept clojure.core.server/repl}' -M -r"
Benchmark 1: echo ':repl/quit' | clj -J-Dclojure.server.repl='{:port 5555 :accept clojure.core.server/repl}' -M -r
  Time (mean ± σ):      1.376 s ±  0.137 s    [User: 1.631 s, System: 0.212 s]
  Range (min … max):    1.194 s …  1.606 s    10 runs
```

Under 1.5 seconds. Let's see how long it takes to do the same thing using [Leiningen](https://leiningen.org/).

```text
λ hyperfine --warmup 3 'echo "(exit)" | lein repl'
Benchmark 1: echo "(exit)" | lein repl
  Time (mean ± σ):      3.477 s ±  0.045 s    [User: 4.039 s, System: 0.551 s]
  Range (min … max):    3.427 s …  3.560 s    10 runs
```

Over twice as long. That's understandable, because comparing `clj` to Leiningen is not exactly apples to apples. Leiningen not only loads more code than `clj`, it also starts an [nREPL](https://nrepl.org/) server and connects to it. Still, there's no way (that I know of) to go any faster than that using Leiningen, so I suppose the comparison isn't completely unfair.

To be thorough, let's eliminate Leinigen from the equation and run an nREPL server using `clj`:

```text
λ hyperfine --warmup 3 "echo '(exit)' | clj -Sdeps '{:deps {nrepl/nrepl {:mvn/version \"0.9.0\"}}}' -M -m nrepl.cmdline --interactive"
Benchmark 1: echo '(exit)' | clj -Sdeps '{:deps {nrepl/nrepl {:mvn/version "0.9.0"}}}' -M -m nrepl.cmdline --interactive
  Time (mean ± σ):      2.722 s ±  0.180 s    [User: 4.716 s, System: 0.383 s]
  Range (min … max):    2.584 s …  3.204 s    10 runs
```

Over half a second faster. That's still twice as long as `clj` and a socket server, but it's not too bad.

The upshot is that if you want to minimize your REPL startup time, use `clj` instead of Leiningen. The penalty nREPL carries isn't huge, but if you want to go as fast as possible, use the socket server to run [a `clojure.main` REPL](https://clojure.org/reference/repl_and_main) (or [prepl](https://clojuredocs.org/clojure.core.server/prepl), if you prefer, although editor support for that is slim) instead. Opting for `clj` and socket server shaves off around two seconds of your REPL startup time.

## Just a spoonful of sugar

If you like the idea of using `requiring-resolve` to avoid top-level requires but find too verbose, here's a little bit of sugar you might consider sprinkling on your `user.clj`:

```clojure
(defn ^:private rapply
  "Given a qualified symbol naming a function and a variable number of args to
  it, require the function and apply it to args."
  [sym & args]
  (apply (requiring-resolve sym) args))
```

Armed with that, `init`, for example, becomes:

```clojure
(defn init []
  (alter-var-root #'system
    (constantly
      (rapply 'my.app/example-system {:host "dbhost.com" :port 123}))))
```

Not a huge difference in readability here, granted, but you might find it useful if if you have many helper functions in your `user.clj`.

## Wrap up

Using one of my work projects as a test subject, using `requiring-resolve` to avoid requiring anything at the top level of my `user.clj` brought down the REPL startup time for that project from \~23 seconds to 1.6 seconds. After precompiling dependencies, Clojure loads all the libs the app needs in 5.4 seconds, instead of the 12 seconds sans precompilation.

Those two changes have the biggest impact on REPL startup time. If you want to go further, though, consider using `clj` instead of Leiningen to shave off a couple of more seconds. Finally, using a socket server instead of nREPL might win you an additional second or so.

## Why, though?

You might think that running a REPL with only clojure.core (and a few auxiliary libs, like clojure.repl) loaded isn't particularly useful. To me, it is: that's all I need to start evaluating things from my editor. Once I have clojure.core loaded, to start working with a specific part of my app, I can evaluate individual function definitions or namespaces and start using them. I find it's not necessary to always have the entire app loaded into your runtime. When I need that, though, precompiling my dependencies helps me do that much faster, too.

## Prior art

 A lot of virtual ink has been spilled on this topic, so this is by no means an exhaustive list, but here's some more reading on the topic of Clojure startup times.

- [Improving Clojure Start Time](https://web.archive.org/web/20170216042612/http://dev.clojure.org/display/design/Improving+Clojure+Start+Time) by Alex Miller
- [Why Clojure starts up slowly — is it really the JVM](https://ericnormand.me/article/the-legend-of-long-jvm-startup-times) by Eric Normand
- [Clojure's slow start — what's inside](http://clojure-goes-fast.com/blog/clojures-slow-start/) by Alexander Yakushev
- [Why is Clojure bootstrapping so slow?](https://blog.ndk.io/clojure-bootstrapping.html) by Nicholas Kariniemi

## Tech specs

Here's the Java version and hardware specs I ran these tests on:

```text
λ java -version
openjdk version "18" 2022-03-22
OpenJDK Runtime Environment (build 18+36-2087)
OpenJDK 64-Bit Server VM (build 18+36-2087, mixed mode, sharing)

λ system_profiler SPHardwareDataType
…
Model Name: MacBook Pro
Model Identifier: MacBookPro16,2
Processor Name: Quad-Core Intel Core i5
Processor Speed: 2 GHz
Number of Processors: 1
Total Number of Cores: 4
Memory: 16 GB
…
```

For what it's worth, while running the benchmarks, I noticed that compared to Java 11, Java 18 (and presumably 17, which is the latest long-term support release) shaves something like half a second off the startup time. So that's another thing you might want to consider if you're looking to make your REPL start as fast as possible. I didn't really have time to do a lot of science on that yet, though.
