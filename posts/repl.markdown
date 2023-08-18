---
title: The anatomy of a read-eval-print loop
date: 2023-08-16
status: draft
---

<!--
- Clojure, eval->print even more powerful because of non-opaque (limpid, lucid, plain) data
- Clojure has datafication, a process that turns an opaque object into plain data
-->

>It is common to conflate any interactive language prompt with a REPL, but I think it is an important aspect of Lisp REPLs that they are a composition of  read-eval-print.
>
>—Rich Hickey, "A History of Clojure" (2020)

```svgbob
              +----+                                           +-----+
 .------------| IN |                              .--chars --> | OUT |
 |            +----+                              |            +-----+
 |                                                |
 v                                                |
 |            .---.            .---.            .---.          .---.
 *--chars --> | R | --data --> | E | --data --> | P | -------> | L |
 |            '---'            '---'            '---'          '---'
 |                                                               |
 '-------------------------------<-------------------------------'
```

REPL is short for "read-eval-print loop". Many programming languages purport [to](https://docs.oracle.com/en/java/javase/20/jshell/introduction-jshell.html#GUID-630F27C8-1195-4989-9F6B-2C51D46F52C8) [have](https://nodejs.org/api/repl.html#repl) [one](https://www.jetbrains.com/help/idea/kotlin-repl.html#kotlin-repl). Here's a rough sketch of how what most of these languages call a REPL works:

1. **Read** a chunk of user input into memory.
2. **Evaluate** the read string.
3. **Print** a string representation of the evaluation result.
4. **Loop** back to the beginning.

It is rare that you are able to (or, indeed, interested in) altering any of these steps. What would it even mean to swap out the "read" step of a Node.js or Kotlin REPL for something else?

In [Clojure](https://clojure.org) (and other [Lisps](https://en.wikipedia.org/wiki/Lisp_(programming_language))), however, R, E, and P are discrete, exchangeable steps. You can, for example, make an R that rewrites parts of your code before handing it off to E. You can make an E that evaluates the code you give it in the context of your choosing. Or, you can make a P that, instead of printing the evaluation result into [standard output](https://www.gnu.org/software/libc/manual/html_node/Standard-Streams.html#index-stdout), puts it into a [database](https://github.com/quoll/asami), or sends it to the data visualization tool [of](https://github.com/eerohele/tab) [your](https://github.com/djblue/portal) [choice](https://clojure.org/news/2023/04/28/introducing-morse).

`<<<INTRO>>>`

## R is for read

The R in REPL is often taken to mean "to read a line from standard (or other) input". In Clojure and other Lisps, however, to "read" means to extract a **form** from a character stream. A form is *not* a string; it is [code represented as data](https://www.expressionsofchange.org/dont-say-homoiconic).

Here's an example of a form:

```clojure
(inc 1)
```

This form is [Clojure data structure](https://clojure.org/reference/data_structures) representing a piece of code. This particular form is a [persistent list](https://clojure.org/reference/data_structures#Lists). Its first element is a [symbol](https://clojure.org/reference/reader#_symbols) naming the function `inc`. The second element of the list is the number `1`, an argument to that function.

Because the form is a data structure, you can manipulate it using the functions and macros in [the Clojure standard library](https://clojure.org/api/cheatsheet) before handing it off to E for evaluation. For example, you can change the `inc` (increment) to `dec` (decrement):

```clojure
user=> (cons 'dec (rest '(inc 1)))
(dec 1)
```




The R in a standard Clojure REPL is based on [`read`](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/read), a function that reads forms one at a time from a character stream. By default, that character stream [is](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/*in*) the [standard input](https://www.gnu.org/software/libc/manual/html_node/Standard-Streams.html#index-stdin). Clojure allows you to plug in any [compatible character stream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/PushbackReader.html), though.

Here's `read` hard at work, reading a form from a character stream:

```clojure
user=> (read (java.io.PushbackReader. (java.io.StringReader. "(+ 1 2 3)")))
(+ 1 2 3)
```

By default, R passes its return value to E as is. Since in Lisp REPLs the R is customizable, we can make a REPL whose R does a bit more.

Clojure has a function called `tap>` that's often (but not exclusively) used for [inspecting values at runtime](https://dev.to/hlship/debugging-clojure-at-the-repl-using-tap-2pm5). The `tap>` function does not return the value of its argument, though, which makes it a bit tricky to use in some situations, such as in threading macros.

We could make our own version of `tap>` that returns its argument. However, ensuring that our custom function is in scope without having to use a fully-qualified path or requiring it in every namespace is cumbersome. Instead, we'll make a REPL whose R supports a special symbol of our own devising, `?>`. Whenever our special R sees `?>` somewhere in the code, it rewrites that part of the code to call `tap>` such that it returns its argument.

```clojure
user=> (clojure.main/repl
         :read
         (fn [request-prompt request-exit]
           ;; Call the default R implementation.
           (let [form (clojure.main/repl-read request-prompt request-exit)]
             (clojure.walk/prewalk
               (fn [sub-form]
                 ;; If there's a form with the shape of (?> x) anywhere
                 ;; in the code, rewrite it into (doto x tap>).
                 (if (and (list? sub-form) (= '?> (first sub-form)))
                   (concat '(doto) (rest sub-form) '(tap>))
                   sub-form))
               form))))
;; Print every `tap>` argument into standard output.
user=> (add-tap (fn [x] (prn '?> x)))
nil
;; Try it out
user=> (?> (inc 2))
?> 3 ; Standard output
3 ; Evaluation result
```

Even though a Clojure REPL reads Clojure forms by default, there's nothing stopping you from making an R that reads from its input on a by-character or by-line basis.

A standard Clojure REPL does not stop reading when it encounters a newline. This means that if you type an incomplete form such as `(inc 1` into a REPL, instead of throwing a syntax error, Clojure waits for additional input, expecting to encounter the closing parenthesis eventually.

If a syntax error is what you'd prefer, you make a REPL that reads forms in a line-oriented fashion:

```clojure
user=> (clojure.main/repl
         :read
         (fn [_ _] (read-string (read-line))))
```
```
user=> (inc 1
Execution error at user/eval1$fn (REPL:3).
EOF while reading
```

<!-- EVAL -->
```clojure
(defn square
  {:test (fn [] (assert (= 3 (square 2))))}
  [x]
  (* x x))

(clojure.main/repl
  :eval
  (fn [form]
    (let [ret (eval form)]
      (clojure.walk/prewalk
        (fn [sub-form]
          (if (and
                (list? sub-form)
                (= #'clojure.core/defn (-> sub-form first resolve)))
            (let [v (-> sub-form second resolve)]
              (when (-> v meta :test)
                (test v)))))
        form)
      ret)))
```

The ability to `read` in this manner is only available to languages that [use the same data structures for both code and data](https://www.expressionsofchange.org/dont-say-homoiconic). Languages such as Java or Node.js therefore do not support "reading" in the sense Lisps do. If they did, if you typed `1 + 2 + 3` into a Node.js interpreter, Node.js would turn the string `1 + 2 + 3` into [a JavaScript array](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array) of items you could manipulate using the array manipulation methods built into JavaScript before handing the code off for evaluation (E).

<!--
  Mention input/output stream rebinding!
-->

<!--
Going even further, the ability to swap in your own R, E and P means that you can reappropriate a Lisp REPL into something else altogether. You can turn a REPL into a unit-aware calculator, an interface for interacting with a large language model, or an interpreter for another programming language.

Another defining feature of Lisp REPLs is that you can run a new REPL from within an existing REPL. Here's an example:

```clojure
(clojure.main/repl
  :prompt (fn [] (println "What's your name? "))
  :read (fn [_ _] (read-line))
  :eval (fn [name] (printf "Hello, %s!\n" name)))
```

If you execute that code in an existing Clojure REPL, you will find yourself in a new REPL that will commence a sustained inquiry regarding your name.


That REPLs can nest like [Matryoshka dolls](https://en.wikipedia.org/wiki/Matryoshka_doll) requires that the input of the R in the top-level REPL (the outermost doll) be unadorned with any sort of framing carrying (usually) metadata related to the code. That is, the input to R must be:

```clojure
(inc 1)
````

Instead of this:

```clojure
{:op "eval" :code "(inc 1)" :ns "user" :file "user.clj" :line "3" :column "1"}
```

If you find that the input to R is enfolded in an envelope like this, you are not sitting at a REPL, but instead a remote procedure call (RPC) server [of some sort](https://nrepl.org/nrepl/1.0/index.html).

If you are, that is not a bad thing. RPC-style message framing has many benefits: being able to bundle input and metadata makes it straightforward to assign file, line and column number for use in error messages. Specifically to Clojure, having easy access to both code and the [namespace](https://clojure.org/reference/namespaces) in whose context to both read (R) and evaluate (E) said code is very helpful.

```clojure
=> {:op "eval" :code "(read)"}
<= {:status #{:need-input}}
=> {:op "stdin" :stdin "(inc 1)"}
<= {:ns "user" :value "(inc 1)"}
```
-->
<!--
If having a REPL as your initial communication protocol is a Matryoshka doll, having an RPC server instead is a Matryoshka doll filled with concrete.
-->



<!--
Reading needs ns context, too, because of e.g. auto-qualified keywords
  -->

<!--
You can make your own REPL that throws an exception if the form you try to evaluate calls a deprecated function, for example. Or, instead of printing the evaluation result into the [standard output](https://www.gnu.org/software/libc/manual/html_node/Standard-Streams.html#index-stdout) stream, you can make a REPL that stores all evaluation results in a database, or sends the result to the data visualization tool of your choice.

To run a new REPL, you need not abandon your existing REPL. Instead, you reappropriate

The ability to do this relies on the simplicity of the protocol the REPL uses for both input and output. Contrast this with RPC-style protocols like nREPL. nREPL is short for "Network REPL". "Not a REPL" would be a more fitting moniker.

<aside>Do not construe this as a criticism of nREPL. nREPL is a fine tool, and one for whose existence the Clojure community can be grateful for.</aside>

```clojure
;; Input
{:op "eval" :code "(inc 1)"}

;; Output
{:session "4cee2336-1977-4049-af82-5c5e8ecfeebe", :ns "user", :value "2"}
{:session "4cee2336-1977-4049-af82-5c5e8ecfeebe", :status #{:done}}
```

In fact, the ability to swap out the first three letters in "REPL" means that you can reappropriate a Lisp REPL into something else altogether. You can turn one into a unit-aware calculator, an interpreter for a completely different language, or an interface for interacting with a large language model.
-->

<!--
You can even turn a Clojure REPL into a Java, uh, "REPL", if you want. Behold:

```clojure
(let [jshell (jdk.jshell.JShell/create)
      eval-counter (atom 0)]
  (clojure.main/repl
    :init
    (fn [] (reset! eval-counter 0))

    :prompt
    (fn []
      (print "\njshell> ")
      (flush))

    :read
    (fn [_ request-exit]
      (let [input (read-line)]
        ;; Ctrl+D to exit
        (if (nil? input)
          request-exit
          input)))

    :eval
    (fn [string] (.eval jshell string))

    :print
    (fn [events]
      (run!
        (fn [event]
          (println (str "$" (swap! eval-counter inc)) "==>" (.value event)))
        events))))
```

You can then bask in Java's exactitude and pith from the comfort of your Clojure REPL:

```java
jshell> import java.util.stream.Collectors;
$1 ==> nil
jshell> import java.util.stream.Stream;
$2 ==> nil
jshell> var stream = Stream.of("a", "b", "c");
$3 ==> java.util.stream.ReferencePipeline$Head@64bfbc86
jshell> stream.filter(s -> s.contains("b")).collect(Collectors.toList());
$4 ==> [b]
```

In contrast, reading, evaluation, and printing are not discrete steps in JShell and its ilk. You cannot nest a Clojure REPL within JShell.
-->

<!--
With a REPL in hand, your imagination's the limit of what you can do.
-->

<!--
Inserting code into the REPL is 100% the same thing as reading the code from a file (unlike e.g. Java).
-->

<!--
1. **Read** a chunk of characters that constitute a *form* (data) into memory.
2. **Evaluate** the form to yield a *value* (data).
3. **Print** a string representation of the value (data).
4. **Loop** back to the beginning.
-->

<!-- The REPL is a foundational technology that's -->
<!--
In this article, I aim to demonstrate what sets Lisp REPLs apart from interactive shells most other languages have. My background is in Clojure. I'll therefore use Clojure in all code examples in this article. I have little to no experience with Common Lisp, Scheme, or other precursors of Clojure. I am aware that Common Lisp, for example, has a more sophisticated REPL than Clojure, but I'm unqualified to discuss the particulars of that subject.
-->
<!--
## Read

The *read* step of REPLs is often taken to mean "to read a line from standard (or other) input". In Clojure and other Lisps, however, "reading" has a specific meaning. The [`read`](https://www.cs.cmu.edu/Groups/AI/html/cltl/clm/node188.html#SECTION002611000000000000000) function takes a group of characters and turns them into something called a **form**. In Clojure, a [form](https://www.cs.cmu.edu/Groups/AI/html/cltl/clm/node56.html#SECTION00910000000000000000) is any value Clojure can evaluate to produce a new value. Here are examples of forms:

- `42`
- `filter`
- `{:a :b}`
- `(+ 1 2)`

Here's an example of reading a form from a string:

```clojure
user=> (read-string "(+ 1 2 3)")
(+ 1 2 3)
```

Here, `read-string` returns the form `(+ 1 2 3)`. It is an immutable list you can manipulate using the functions in the Clojure standard library. For example, given `(+ 1 2 3)`, you can change the plus to a minus:

```clojure
;; *1 is a reference to the previous evaluation result; here, (+ 1 2 3).
;;
;; cons is a function that, given a value and a list, prepends the value into
;; the list.
;;
;; rest is a function that returns its input, sans the first element (in this
;; case, (1 2 3).
user=> (cons '- (rest *1))
(- 1 2 3)
```

This is obviously not a useful example. Its point is to demonstrate the malleability that having a distinct reading step offers.

The ability to `read` in this manner is only available to languages that [use the same data structures for both code and data](https://www.expressionsofchange.org/dont-say-homoiconic). Languages such as Java or Node.js therefore do not support "reading" in the sense Lisps do. For example, if you type `1 + 2 + 3` into a Node.js interpreter, Node.js does not first turn the string `1 + 2 + 3` into [a JavaScript array](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array) of items you would then be able to manipulate using the array manipulation methods built into JavaScript. The concept of "reading" in this sense does not exists

Reading being a distinct step means you can extend the default `read` implementation or replace it with your own. For example, you can make a reader function that it otherwise the same as the regular `read` function, except that it throws an exception if you try using a deprecated function or macro.
-->
<!--

Here's a Clojure function that, given a form, throws an exception if there's a reference to a deprecated [Var](https://clojure.org/reference/vars) anywhere in the form. (Understanding the function requires some fluency in Clojure. If you don't have that, just skip it: the implementation details are not important here.)

```clojure
(defn throw-on-deprecated
  "Given a form, throw an exception if the form refers to a deprecated var."
  [form]
  (when (seq? form)
    (clojure.walk/prewalk
      (fn [form]
        (when (symbol? form)
          (let [v (resolve form)]
            (when (-> v meta :deprecated)
              (throw (ex-info (format "Call to deprecated var: %s." (pr-str v))
                       {:var v})))))))))
```

To use the function above, you'd use `clojure.main/repl`, which allows you to swap out your own implementations of reading, evaluation, and printing:

```clojure
;; Run a new REPL that throws an exception when it reads a form that refers to
;; a deprecated var.
user=> (clojure.main/repl
         :read
         (fn [_ _]
           (doto (read *in*) throw-on-deprecated)))
user=> (inc 1)
2
user=> (replicate 1 2)
```

Yields:

```
Execution error (ExceptionInfo) at user/eval8195$fn$fn (user:NO_SOURCE_FILE).
Call to deprecated var: #'clojure.core/replicate.
```

-->
<!--
## Evaluate

The `eval` function accepts a form and returns data.
-->
<!-- binding example! e.g. print-length, etc. maybe warn-on-reflection? -->
<!-- The only limits that apply to customizing your REPL are the limits of your programming language. -->
<!--
Given:

```clojure
(clojure.main/repl
  :eval
  (fn [form]
    (binding [*warn-on-reflection* true]
      (let [ret (eval form)]
        ;; Some REPL implementations don't auto-flush after writing to the
        ;; error stream. We'll therefore flush manually after
        ;; evaluation to make sure the reflection warning becomes
        ;; visible.
        (flush *err*)
        ret))))
```

Then:

```clojure
(defn upper-case
  [s]
  (.toUpperCase s))
```

Yields:

```
Reflection warning, my.clj:3:3 - reference to field toUpperCase can't be resolved.
#'user/upper-case
```

## Print

```clojure
(clojure.main/repl
  :print (fn [value] (tab.api/tab> user/tab value)))

(clojure.reflect/reflect java.time.Clock)
```

## Tradeoffs

The


- Discuss intermingling of evaluation results and standard output
- Discuss difficulty of clients linking inputs to outputs (e.g. inline results)
-->

<!--
When you build on a REPL, you (or your editor) could easily swap between REPL and RPC communication modes. In contrast, it is not possible to go from RPC to REPL.

Demonstrate nesting REPLs and escaping back into the
-->

<!--
Nested REPL that:
- Stores the previous input and output or exception
- Submits them to ChatGPT for analysis.
-->

<!--
- ChatGPT example
- use clojure.main/repl
-->

<!-- ClojureScript eval yields strings, not data -->

<!--
nREPL and other RPC-style protocols complect (drink) reading and evaluation: since there is no separate read step, it is not possible to customize.

Can't use nREPL to start a ChatGPT REPL.
-->

<!--
The REPL is a substrate. It is the simplest possible protocol: character streams in, character streams out.
-->

<!--
Being a Lisp, Clojure has a plethora of REPLs. It has [nREPL](https://nrepl.org), which is probably the most widely used tool of any kind in the entire Clojure ecosystem. Clojure has a built-in [REPL](https://clojure.github.io/clojure/clojure.main-api.html#clojure.main/repl), which you can [serve over a network socket](https://clojure.org/reference/repl_and_main#_launching_a_socket_server). Clojure also has [prepl](https://clojuredocs.org/clojure.core.server/prepl), a (strangely underdocumented) REPL that almost no one uses, which yields "structured output (for programs)." There's also [Unrepl](https://github.com/Unrepl/unrepl), which upgrades Clojure's built-in REPL or (nREPL, somehow, apparently) to use an extensible protocol for communicating with the Clojure runtime.
-->

<!--
- REPL to RPC vs. RPC to REPL
- Nested REPLs (ClojureScript, nREPL)
-->

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

<!--
  ```clojure
(let [a (atom [])]
  (clojure.main/repl
    :read
    (fn [request-prompt request-exit]
      (if
        (zero? (count @a)) (do (swap! a conj (read-line)) request-prompt)
        (take 2 (swap! a conj (read-line)))))

    :eval
    (fn [[from to]]
      (println (clojure.java.process/exec "units" from to)))))
```
  -->