---
title: Programming Clojure without tools.namespace.repl
date: 2022-02-15
---

>Pain is meant to wake us up. —Jim Morrison

For my money, one of the best things about Clojure is the ability to [connect](https://clojure.org/reference/repl_and_main#_launching_a_socket_server) to a running program and poke at its innards. You can inspect and alter the state of your program, call or redefine any of its existing [definitions](https://clojure.org/reference/special_forms#def) (functions or macros, mostly), and, of course, make all-new definitions.

It's not just that you *can* do it, either. It is, in fact, what you, as a Clojure programmer, do all day every day. Well, I hope you do, anyway. If not, well. You're missing out.

Anyway, let me try to describe the way it works in practice. To solve whatever problem you have at hand, you begin by subjecting the little grey cells in your head to the grueling ordeal of coming up with the most efficient way of applying functions to immutable data structures (all the while laying [in your hammock](https://www.youtube.com/watch?v=f84n5oFoZBc), of course). Then, you bash on your keyboard to persuade [your favorite Clojure editor](https://tutkain.flowthing.me) to get the parens just so, then use the facilities of said editor to send your glorious, beautiful new brainchild to the Clojure runtime, with a nicely worded note asking it to kindly start using this new piece of code you painstakingly prepared for it and discard the dusty old thing it's currently using.

The last bit — replacing old definitions in the runtime with new ones — is what I want to talk about in this article. The Clojure community has settled on two main ways to accomplish this task. In my experience, the most commonly used method is the so-called "[reloaded workflow](https://cognitect.com/blog/2013/06/04/clojure-workflow-reloaded)". The way this basically works is that you make your changes, save your files, then run a function called [`clojure.tools.namespace.repl/refresh`](https://clojure.github.io/tools.namespace/#clojure.tools.namespace.repl/refresh), which takes care of scrubbing your runtime clean of any previous definitions and installing new ones.

Let's take a look at a concrete example. Say you have two files: `db.clj` with the namespace `app.db`, and `handler.clj` with `app.handler`, and they look like this:

```clojure
;; src/app/db.clj
(ns app.db
  (:require [next.jdbc.sql :as sql]))

(defn get-pokemon
  [db]
  (sql/query db ["SELECT * FROM pokemon;"]))

;; src/app/handler.clj
(ns app.handler
  (:require [app.db :as db]))

(defn get-pokemon
  [{db :db}]
  {:status 200 :body (db/get-pokemon db)})
```

<!-- https://github.com/aredington/clojure-repl-sufficient-p -->

<!--
## The metaphysics of reloading code

Apart from what I've mentioned above, there are other, more, er, *philosophical* reasons the Reloaded approach doesn't jive with me. These reasons are based not on empirical evidence but personal intuitions, and I therefore do not proffer then as reasons for why anyone else should shy away t.n.r.

One of these reasons is that unlike many other languages, Clojure explicitly does not associate any semantics with files. That is to say, Clojure does not care where your code lives. You could put all your code into SQLite or Datomic for all Clojure cares (and maybe you should? report back if you do). Assigning a special meaning to files when Clojure itself does not feels wrong to me.
-->

<!--

At the core of this workflow is a function called called [`clojure.tools.namespace.repl/refresh`](https://clojure.github.io/tools.namespace/#clojure.tools.namespace.repl/refresh). It is a function that scans your source directories for Clojure files that have changed since the last time you called `refresh`, then reloads those files. The reloading process scrubs your Clojure runtime clean of the definitions you've previously installed and replaces them with the new definitions it finds in your source files.

-->

<!--


Many Clojure applications depend on a library called [tools.namespace](https://github.com/clojure/tools.namespace). The centerpiece of the library is a function called [`clojure.tools.namespace.repl/refresh`](https://clojure.github.io/tools.namespace/#clojure.tools.namespace.repl/refresh). It is a function that scans your source directories for Clojure files that have changed since the last time you called `refresh`, then reloads those files. The reloading process scrubs your Clojure runtime clean of the [definitions](https://clojure.org/reference/special_forms#def) you've previously installed and replaces them with the new definitions it finds in your source files.
-->
<!--

Since, like me, you're probably [an overpaid nerd using Clojure to make web apps](https://gist.github.com/oakes/4af1023b6c5162c6f8f0#why-care-about-rust), let's take a look at an example that'll no doubt look familiar to you.

```clojure
;; src/app/db.clj
(ns app.db
  (:require [next.jdbc.sql :as sql]))

(defn get-pokemon
  [db]
  (sql/query db ,,,))

;; src/app/handler.clj
(ns app.handler
  (:require [app.db :as db]))

(defn get-pokemon
  [{db :db}]
  {:status 200 :body (db/get-pokemon db)})

(defn make-handler [options] ,,,)

;; src/app/http.clj
(ns app.http
  (:require [org.httpkit.server :as http-kit]
            [app.handler :as handler]))

(defn start
  [options]
  (http-kit/run-server (handler/make-handler options)))
```

Sounds pretty good, right? The [rationale for the library](https://github.com/clojure/tools.namespace/tree/c0b333e127e14c2ac6d5b04d14d0e714d08bfdbb#reloading-code-motivation) certainly make a lot of sense at first blush, as does the [blog post](https://cognitect.com/blog/2013/06/04/clojure-workflow-reloaded) that has doubtless driven swathes of Clojure programmers to wear out the unfortunate key they've bound to evaluate `(reset)`.

-->
<!--
- helpful constraint
- a tool for maintaining focus
- hoity-toity
-->

<!--
Workflow reloaded tends to operate at one level of abstraction higher than what I am talking about. It is working at the level of your project, and your files, and your namespaces, and -- and this is something that I do not have in this workflow -- it offers tooling to help you keep track. So it says: "You know what? I am going to make a change in this namespace. That implies cascading changes to that namespace, and that namespace, and that namespace, and I will help you reload them."

I do not have any tools like that to help me. As a result, the way I work is much more targeted surgery. I go in and I make a change to the namespace, and I make a change to a single form. I do not reload the namespace. And if that change is going to cascade other places, then I have to think through that.

And often that is not a problem. Often this technique helps me focus enough that I do not have to worry about it.

But let us think about the moments when it is a problem. Let us say I am working in form foo in namespace A, and I realize that that is going to have a cascading effect. I might be better off to track that down in my head, and think through it, and ask the question: "Why is this effect cascading so much?"

I think that by working at a lower level, you put yourself -- it is that old adage of: "If something hurts, do it all of the time." When I am working at this level, when things slip into becoming unnecessarily dependent on each other, it is in my face. And so it provides a pain point that I think, in my experience, leads to having less coupled code.

-- https://github.com/matthiasn/talk-transcripts/blob/6ad5d48c718aacfb7a4e4deac405b058285dc3e6/Halloway_Stuart/RunningWithScissors.md
-->


<!--
- file-oriented

As Rich Hickey writes in *A History of Clojure*:

>From a language perspective, one aspect of supporting REPL-driven development is that there are no language semantics in Clojure associated with files or modules. While it is possible to compile and load files, the effect of such loading is always as if executing each contained expression sequentially.

-- https://download.clojure.org/papers/clojure-hopl-iv-final.pdf


- prohibits https://clojure.org/guides/dev_startup_time

It dilutes the soulstuff our [Dream Machine](https://press.stripe.com/the-dream-machine) is made of. It disfigures our beautiful REPL, turning it back into the batch-processing monstrosity we so feverishly keep running away from.
-->



<!--
If you've watched Stuart Halloway's talk [Running With Scissors: Live Coding with Data](https://www.youtube.com/watch?v=Qx0-pViyIDU),
-->