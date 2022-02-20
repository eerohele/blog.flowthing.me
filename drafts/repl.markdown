---
title: What's in a REPL?
date: 2021-10-28
---

A **REPL**, or a **Read-Eval-Print Loop**, is a …

<!--
A computer program is a list of instructions that tell a computer what to do.
![REPL](images/repl.svg)

For the past couple of years, I’ve been on this fool’s errand to make [my favorite text editor](https://www.sublimetext.com) a viable alternative for programming Clojure.

Like with most projects, in the beginning, I had no idea what I was doing. (I’m not sure I still do, to be honest.) All I knew was that I wanted to evaluate Clojure code directly from my editor and see the result.
-->

<!--
Like, I wanted to be able to do things like this:

First, I want to write a function definition in my editor, like this:

![REPL](images/repl-1.png)

Then, I want to be able to write code that calls that function

![REPL](images/repl-2.png)

Finally, I want to be able to execute that function call by hitting a key binding in my editor and have the result show up in my editor.

![REPL](images/repl-3.png)

 I’ve since realized that back then, and for a long time after, I didn’t even know what a REPL actually was. Most Clojure programmers, I’m sure, are in the same boat.

Most programmers have probably never really even thought about it too much, really. I mean, a REPL is this thing where type code and the computer gives you back the result. Right? That’s the way I thought about it, at least.
-->

<!--
  # What's in a REPL?

Most programmers recognize the acronym "REPL". When they hear "REPL", they think of this thing where you type in code and the computer prints the answer. You know, [`irb`](https://github.com/ruby/irb) for Ruby, [GHCi](https://downloads.haskell.org/~ghc/9.0.1/docs/html/users_guide/ghci.html) for Haskell, [`node`](https://nodejs.org/api/repl.html#repl_repl) for Node.js, and so on.

- irb, GHCi, etc. don't really have R
  - typing code into these has different semantics than specifying code in files
  - Rubyists, Haskellers, etc. typically don't sit at their interactive prompts all day every day developing programs
  - cf. Lisp
- What is "Socket REPL"
  - socket server is actually a more generic thing
  - prepl is just a different accept function
- nREPL is not really a REPL
  - "E" is not really "E"
-->