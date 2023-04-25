---
layout: post
title: clojure.tools.namespace.repl/refresh considered harmful
author: eerohe
date: 2022-02-14
description: Yup.
status: draft
---

<!-- Maybe split into a series instead! -->

So, a bit of an argument of authority for y'all. (Not *my* authority, mind you. This post isn't going to be quite that obnoxious.)

There's a bunch of topics that pop up pretty regularly over at [the Clojurians Slack server](http://clojurians.net/) where there seems to be a fairly heavy consensus among the more veteran Clojure programmers on which path to take. I figured I'd take a stab at distilling some of that advice into a blog post. Well, the advice I'm in agreement with, at least.

<!--
- Prefer java.time over clj-time (which wraps Joda Time) and java.util.Date
- Always use the `init` arity of `reduce`
- Avoid `tools.namespace.repl` (http-kit/Jetty example)
- `case` only works with compile-time literals
- Write docstrings
- Prefer transducers over the thread-last macro for multi-step transformations
-->

## Avoid `tools.namespace.repl/refresh` (and variants thereof)

The `user.clj` of every Clojure project I've ever inherited has had three functions that have looked something like this:

```clojure
(defn start [] ,,,)

(defn stop [] ,,,)

(defn reset
  []
  (stop)
  (tools.namespace.repl/refresh :after 'user/start))
```

[`clojure.tools.namespace.repl/refresh`](https://clojure.github.io/tools.namespace/#clojure.tools.namespace.repl/refresh) is a function that scans your source code directories for files which have changed since the last time you called the function, then reloads those files. The reloading process entails scrubbing the Clojure runtime clean of the current [definitions](https://clojure.org/reference/special_forms#def) first introduced in those files and replacing them with the current definitions.

I mean, that sounds pretty reasonable, right? Many of the points in [the rationale](https://github.com/clojure/tools.namespace/tree/c0b333e127e14c2ac6d5b04d14d0e714d08bfdbb#reloading-code-motivation) certainly make a lot of sense.


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

## Prefer the three-argument arity of `reduce`

The [`reduce`](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/reduce) function has two arities: one with two arguments and one with three.

The two-argument arity is [a carryover](https://clojurians.slack.com/archives/C053AK3F9/p1643807910520289?thread_ts=1643786346.304999&cid=C053AK3F9) from [Common Lisp](http://clhs.lisp.se/Body/f_reduce.htm).

<!-- https://clojurians.slack.com/archives/C03S1KBA2/p1465935526000373 -->

```clojure
(reduce f coll)
(reduce f val coll)
```

The


## Write docstrings

My experience is that developers are generally reluctant to write comments. The consensus seems to be that if you need to write a comment, there's something wrong with your code. That is, either you've done a bad job at either naming or implementing your function.

I belonged to this camp up until fairly recently.

As John Ousterhout writes in *A Philosophy of Software Design*:

>Comments provide the only way to fully capture abstractions, and good abstractions provide the only way to good system design.

>To write a good comment, you must identify the essence of a variable or piece of code: what are the most important aspects of this thing?

>Comments serve as a canary in the coal mine of complexity. If a method or variable requires a long comment, it is a red flag that you don't have a good abstraction.

>The comment that describes a method or variable should be simple and yet complete. If you find it difficult to write such a comment, that's an indicator that there may be a problem with the design of the thing you're describing.