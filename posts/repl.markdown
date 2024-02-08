---
title: The anatomy of a REPL
date: 2024-02-20
description: What sets Lisp REPLs apart from the interactive shells many other languages have?
---

>It is common to conflate any interactive language prompt with a REPL, but I think it is an important aspect of Lisp REPLs that they are a composition of  read-eval-print.
>
>—Rich Hickey, "A History of Clojure" (2020)

```svgbob
              +----+                                           +-----+
 .--chars-----| IN |                              .--chars --> | OUT |
 |            +----+                              |            +-----+
 |                                                |
 v                                                |
 |            .---.            .---.            .---.          .---.
 *--chars --> | R | --data --> | E | --data --> | P | -------> | L |
 |            '---'            '---'            '---'          '---'
 |                                                               |
 '-------------------------------<-------------------------------'
```

REPL is short for "read-eval-print loop". Many programming languages [purport](https://docs.oracle.com/en/java/javase/20/jshell/introduction-jshell.html#GUID-630F27C8-1195-4989-9F6B-2C51D46F52C8) to [have](https://nodejs.org/api/repl.html#repl) [one](https://www.jetbrains.com/help/idea/kotlin-repl.html#kotlin-repl). Here's a rough sketch of how what most of these languages call a REPL works:

1. **Read** a chunk of user input into memory.
2. **Evaluate** the read input.
3. **Print** a string representation of the evaluation result into the standard output.
4. **Loop** back to the beginning.

It is rare that you are able to (or, indeed, interested in) altering any of these steps. What would it even mean to swap out the "read" step of a Node.js or Kotlin REPL for something else?

In [Clojure](https://clojure.org) (and other [Lisps](https://en.wikipedia.org/wiki/Lisp_(programming_language))), however, R, E, and P are discrete, exchangeable steps. You can, for example, make an R that rewrites parts of your code before handing it off to E. You can make an E that, every time you redefine a function, runs the tests for that function. Or, you can make a P that, instead of printing the evaluation result into [standard output](https://www.gnu.org/software/libc/manual/html_node/Standard-Streams.html#index-stdout), puts it into a [database](https://github.com/quoll/asami), or sends it to the data visualization tool [of](https://github.com/eerohele/tab) [your](https://github.com/djblue/portal) [choice](https://clojure.org/news/2023/04/28/introducing-morse).

In this article, I will examine what sets a Lisp REPL apart from an interactive language shell and explore some of the opportunities a having access to a REPL offers. I will use Clojure in all of the examples, because that's the Lisp I'm most familiar with.

I will give examples of how and why you might customize each of R, E, and P. These examples are not complete, polished solutions, but I hope they suffice to inspire you to give the REPL a try and perhaps spur you to discover the possibilities of the REPL yourself. Finally, we'll look at an alternative to a REPL: a Remote Procedure Call (RPC) server that knows how to evaluate code you send it. We'll discuss how it differs from a REPL and why you might want to prefer it to a REPL.

To get started, let's take a look at R: read.

## R is for read

The R in REPL is often taken to mean "to read a line from standard (or other) input". In Clojure and other Lisps, however, to "read" means to extract a **form** from a character stream. A form is *not* a string; it is [code represented as data](https://www.expressionsofchange.org/dont-say-homoiconic).

Here's an example of a string:

```clojure
;; String
"(inc 1)"
```

In contrast, here's an example of a form:

```clojure
;; Form
(inc 1)
```

The form `(inc 1)` is a [Clojure data structure](https://clojure.org/reference/data_structures) representing a piece of code. Specifically, it is an [immutable list](https://clojure.org/reference/data_structures#Lists). The first element of the list is a [symbol](https://clojure.org/reference/reader#_symbols) naming the function `inc`. The second element of the list is the number `1`, an argument to that function.

Because the form is a data structure, you can manipulate it using the functions and macros in [the Clojure standard library](https://clojure.org/api/cheatsheet). For example, you can (if you really want to) change `inc` (increment) to `dec` (decrement):

```clojure
;; rest returns everything but the first item of a sequence.
;;
;; cons appends a new item to the front of a sequence.
;;
;; The single quote tells Clojure not to evaluate (inc 1) before handing it off
;; to rest.
user=> (cons 'dec (rest '(inc 1)))
(dec 1)
```

(The ability to alter code before evaluation might bring the word _macro_ to mind. Rightly so: having a distinct read step also powers Lisp [macros](https://aphyr.com/posts/305-clojure-from-the-ground-up-macros).)

The R in a standard Clojure REPL is a slightly modified version of [`read`](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/read), a function that reads forms from a character stream, one at a time. By default, that character stream [is](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/*in*) the [standard input](https://www.gnu.org/software/libc/manual/html_node/Standard-Streams.html#index-stdin). Clojure allows you to plug in any [compatible character stream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/PushbackReader.html), though.

Here's `read` hard at work, reading a form from a character stream:

```clojure
;; Turn a string into a stream of characters.
user=> (def reader
         (-> "(+ 1 2 3)" java.io.StringReader. java.io.PushbackReader.))
;; Read a form from the character stream.
user=> (read reader)
(+ 1 2 3) ; a form, not a string
```

There is a downside to reading forms one at a time, however. If you type an incomplete form such as `(inc 1` (with a missing closing parenthesis) into a REPL, by default, Clojure does not throw a syntax error. Instead, it waits for additional input, expecting to encounter the closing parenthesis eventually.

Here's where customizing the R of a REPL comes into play. To have Clojure throw an error when you type an S-expression with a missing closing parenthesis, you can make a REPL that reads forms in a line-oriented fashion, rather than form-oriented. Here's one way to do it:

```clojure
user=> (clojure.main/repl
         :read
         (fn [_ _] (read-string (read-line))))
⁣user=>⁣ (inc 1
⁣⁣Execution error at user/eval1$fn (REPL:3).
EOF while reading⁣⁣
```

Here, when Clojure sees the incomplete form `(inc 1`, it throws an error instead of waiting for more input.

The ability to `read` code in this manner is only available to languages that [use the same data structures for both code and data](https://www.expressionsofchange.org/dont-say-homoiconic). Languages such as Java or Node.js do not support "reading" in the sense Lisps do. If they did, if you typed `1 + 2 + 3` into a Node.js interpreter, Node.js would turn the string `1 + 2 + 3` into [a JavaScript array](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array) of items. You could then manipulate that array using the array manipulation methods built into JavaScript. Only then would it hand the code off for evaluation (E).

Now that we know how R works and looked at a couple of different ways to customize it, let's move on to E for evaluate.

## E is for evaluate

The E in REPL accepts data. Specifically, it accepts [forms R gives it](#r-is-for-read). In most other languages, E accepts a string, and evaluates the code in that string. In Clojure, evaluating a string yields the string itself. Here's an example:

```clojure
;; Evaluating a string.
user=> (eval "(inc 1)")
"(inc 1)"

;; Evaluating a form.
user=> (eval '(inc 1))
2
```

E returns its result as data. It does *not* return a string representation of the evaluation result. This has three consequences:

1. You can manipulate the **input** of E.
1. You can manipulate the **output** of E.
1. E gives P **data**, not a string.

Because E both accepts and yields data, we can, for example, make a REPL that, every time you redefine a function, runs the tests for that function. To do that, let's first write a function with a test:

```clojure
user=> (defn square
         ;; Using :test metadata like this is not a common way of writing
         ;; Clojure tests. I use it here because it makes for a self-
         ;; contained and colocated example.
         {:test #(assert (= 4 (square 2)))}
         [x]
         ;; The extra x here is an intentional bug to make the test fail.
         (* x x x))
#'user/square
```

To [test the test](https://knowyourmeme.com/memes/xzibit-yo-dawg), we can use [`clojure.core/test`](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/test):

```clojure
user=> (test #'square)
⁣⁣Execution error (AssertionError) at user/fn (NO_SOURCE_FILE:5).
Assert failed: (= 4 (square 2))⁣⁣
```

Works great. Now that we have a function and a test, let's make a REPL:

```clojure
user=>⁣ (clojure.main/repl
         ;; Suppress the user=> prompt for this demo.
         :prompt (constantly nil)

         :eval
         (fn [form]
           ;; Evaluate the form as per usual.
           (let [ret (eval form)]
             ;; After evaluation, walk through every form nested inside the
             ;; top-level form given to eval (usually, a list).
             (clojure.walk/prewalk
               (fn [sub-form]
                 ;; If the first element of a form is defn...
                 (if (and
                       (list? sub-form)
                       (= #'clojure.core/defn (-> sub-form first resolve)))
                   (let [v (-> sub-form second resolve)]
                     ;; If the defn has a :test metadata entry...
                     (when (-> v meta :test)
                       ;; Run the tests defined in that metadata entry.
                       (test v)))))
               form)
             ;; Return the return value of the evaluation.
             ret)))
```

Once you run this REPL, every time you evaluate a form that defines a function (such as `(defn square ...)`, your REPL will re-run the test defined under the `:test` [metadata](https://clojure.org/reference/metadata) key (if any). For example, if you re-evaluate the same exact `(defn square ...)` form from above again, you get:

```clojure
⁣⁣Execution error (AssertionError) at user/fn (NO_SOURCE_FILE:5).
Assert failed: (= 4 (square 2))⁣⁣⁣
```

You're getting a test watcher and auto-runner for pretty cheap there. And you don't have to wait for someone to implement this feature in your programming environment, either.

Another useful way to customize E is to make every evaluation use a certain set of dynamic bindings. Clojure has a small number of thread-local bindings that you can use to configure [reading](#r-is-for-read), [evaluation](#e-is-for-evaluate), [printing](#p-is-for-print), and performance-related warnings.

For example, if the Clojure code you're writing uses [Java interoperability](https://clojure.org/reference/java_interop), you almost certainly want to see warnings when Clojure has to resort to reflection to figure out which method overload to call. One way to do that is to add `(set! *warn-on-reflection true)` into your Clojure namespace (and you should always do that anyway when doing Java interop). Sometimes you might forget, though. To combat your forgetful nature, you can make an E that sets `*warn-on-reflection*` to `true` for every evaluation:

```clojure
user=> (clojure.main/repl
         :eval
         (fn [form]
           (binding [*warn-on-reflection* true]
             ;; Evaluate the form as per usual.
             (let [ret (eval form)]
               ;; Some REPL implementations don't auto-flush after writing to
               ;; the error stream. We'll therefore flush manually after
               ;; evaluation to make sure any possible reflection warnings
               ;; become visible.
               (flush *err*)
               ;; Return the evaluation result.
               ret))))
;; Define a function without specifying a type hint.
;;
;; This forces Clojure to use reflection on the function argument to figure
;; out which .toUpperCase to call.
user=> (defn upper-case
         [s]
         (.toUpperCase s))
⁣⁣Reflection warning my.clj:3:3 - reference to field toUpperCase can't be resolved.⁣⁣
#'user/upper-case
```

Success!

Now that we've seen how you can customize E to make your evaluations extra powerful, let's move on to a take a brief look at P is for print.

## P is for print

The P in REPL accepts the evaluation result as data and, by default, prints it into the standard output stream. P does *not* accept a string representation of the evaluation result. P might *print* a string representation of the evaluation result.

Perhaps the most obvious interesting thing to do with P is to send the result to a tool that can represent the evaluation result in a format more digestible than a string representation. For example, say you want to see the superclasses, fields, and methods of a Java class. Naturally, you'll reach for [`clojure.reflect/reflect`](https://clojure.github.io/clojure/clojure.reflect-api.html#clojure.reflect/reflect). To your dismay, however, you see that the string representation of the data structure it yields is too large to be digestible either inline in your editor or in a separate output panel:

```clojure
;; Only print the first three items of each collection.
user=> (set! *print-length* 3)
nil
user=> (pprint (clojure.reflect/reflect java.time.Clock))
{:bases #{java.lang.Object java.time.InstantSource}
 :flags #{:public :abstract}
 :members
 #{#clojure.reflect.Method{:name system
                           :return-type java.time.Clock
                           :declaring-class java.time.Clock
                           ...}
   #clojure.reflect.Method{:name withZone
                           :return-type java.time.Clock
                           :declaring-class java.time.Clock
                           ...}
   #clojure.reflect.Method{:name tickMillis
                           :return-type java.time.Clock
                           :declaring-class java.time.Clock
                           ...}
   ...}}
nil
```

Luckily, the Clojure ecosystem has a [wealth](https://clerk.vision) [of](https://docs.cider.mx/cider/debugging/inspector.html) [tools](https://github.com/vlaaad/reveal) that know how to visualize Clojure data structures. We can use one of those tools to make a REPL whose P sends the evaluation result it receives from E to such a data visualizer.

For this article, we'll use [Tab](https://github.com/eerohele/tab) (the decidedly least impressive such tool) to turn the big [hash map](https://clojure.org/reference/data_structures#Maps) `clojure.reflect/reflect` returns into a bunch of tables:

```clojure
;; If using Clojure 1.12 or newer, add a dependency into the Clojure runtime.
user=> (clojure.repl.deps/add-lib 'io.github.eerohele/tab)
[io.github.eerohele/tab]
;; Require Tab.
user=> (require '[tab.api :as tab])
nil
;; Run Tab.
user=> (def tab (tab/run))
#'user/tab
;; Run a REPL that, instead of printing every evaluation result into stdout,
;; sends them to Tab, a tool for visualizing Clojure data as tables.
user=> (clojure.main/repl :print (fn [value] (tab/tab> tab value)))
;; Reflect on the nature of a Java class, the Clojure way.
user=> (require '[clojure.reflect :as reflect])
nil
user=> (reflect/reflect java.time.Clock)
```

Here's a screenshot of Tab visualizing the output of `clojure.reflect/reflect`:

!["A screenshot of Tab, a tool for visualizing Clojure data as tables."](tab.png)

(You probably can't tell from the screenshot, but the tables Tab makes are mildly interactive: you can expand, collapse, and zoom in on nested tables.)

Whether that is more or less digestible than a pretty-printed, textual representation is a matter of taste, of course. The point is that unlike the interactive shells of many other languages, a REPL affords you the complete liberty to decide how to represent your evaluation results.

## Putting it all together

To see how to customize each of R, E, and P at once, let's turn a Clojure REPL into a Java shell.

```clojure
(let [jshell (jdk.jshell.JShell/create)
      eval-counter (atom 0)]
  (clojure.main/repl
    :init
    (fn [] (reset! eval-counter 0))

    :prompt
    (fn []
      (println)
      (print "jshell> ")
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

Running this code lets you bask in the exactitude and pith of the Java language from the comfort of your Clojure REPL, like so:

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

It's not that turning a Clojure REPL into a Java shell is necessarily particular useful. It's that _it is possible in the first place_. Since reading, evaluation, and printing are not discrete steps in JShell and its ilk, it is not possible to turn JShell into a Clojure REPL. This is what makes JShell a shell and the Clojure REPL a REPL.

## An alternative

A useful alternative to a REPL is a program that, instead of accepting unadorned Lisp forms, accepts messages wrapped in an envelope such as this:

```clojure
{:id 1 :op :eval :code "(inc 1)" :ns "user"}
```

If you're familiar with [nREPL](https://nrepl.org/), this [message format](https://nrepl.org/nrepl/1.1/design/overview.html#requests) might look familiar to you. There are a number of benefits to using a protocol like this. For one, wrapping your code in an envelope like this allows you to bundle additional context with the code, such as the [namespace](https://clojure.org/reference/namespaces) in whose context to evaluate the code. It also allows the client to match requests with responses. This in turn makes it straightforward to, for example, display evaluation results inline in the client, next to the code you're evaluating.

Additionally, an RPC-style protocol like this is amenable to extension. Besides code evaluation, you can add support for operations such as trafficking editor auto-completion information between the client and the server. In contrast, the only operation a REPL supports is evaluation. To support other operations, the client and the server would need [an auxiliary communication channel](https://groups.google.com/g/clojure-dev/c/Dl3Stw5iRVA/m/IHoVWiJz5UIJ). That decision, while [viable](https://github.com/eerohele/Tutkain/blob/8004698c9895ad0d774fccbfdf4d7f185e4fef00/clojure/src/tutkain/rpc.cljc#L197-L262), comes with its own, significant set of tradeoffs.

All that said, let's try our hand at making an RPC server of our own.

```clojure
;; There's quite a lot of code in this sample. Feel free to skip it if you're
;; not particularly interested in a half-baked RPC server that knows how to
;; evaluate Clojure.

(ns my.rpc
  (:require [clojure.core :as core]
            [clojure.pprint :as pprint]
            [clojure.main :as main])
  (:import (clojure.lang LineNumberingPushbackReader)
           (java.io StringReader)))

;; We'll define this function later.
(declare handle)

;; Save a reference to the standard output for print operations.
;;
;; *out* here could be anything you can write into: a file, s
;; socket, a null writer, etc.
;;
;; We'll need this later.
(def ^{:dynamic true :doc "Original standard output."}
  *$out$* *out*)

(defn read-string*
  "Like clojure.core/read-string, but retains line and column numbers."
  [s]
  (with-open [reader (-> s StringReader. LineNumberingPushbackReader.)]
    (read reader)))

;; Define the default R, E, and P for our RPC server.
(def default-options
  {:read read-string*
   :eval eval
   ;; Pretty-print evaluation results by default, because we can.
   :print pprint/pprint
   ;; As a bonus, allow users to specify a custom init function to e.g. load
   ;; additional code upon startup.
   :init (fn []
           (require '[clojure.repl :refer [doc]])
           (require '[clojure.pprint :refer [pp]]))})

;; Define a function that runs an RPC-style server.
(defn rpc
  ([] (rpc {}))
  ([options]
   (let [{:keys [init] :as options} (merge default-options options)]
     ;; Call the user-supplied init function.
     (init)

     (loop []
       ;; Read a message from the standard input for read operations.
       (let [{:keys [id] :as message} (read *in*)

             recur?
             ;; Rebind standard output such that any print operations are
             ;; wrapped in an envelope (i.e. {:tag :out :val "Hello, world!"})
             (binding [*out* (PrintWriter-on
                                    (fn [x]
                                      ;; Print everything such that it can be
                                      ;; read (via clojure.core/read).
                                      (binding [*print-readably* true]
                                        ;; Write into the standard output writer
                                        ;; we saved a reference to earlier.
                                        (.write *$out$*
                                          ;; Give the response the ID of the
                                          ;; request.
                                          (pr-str {:id id :tag :out :val x}))
                                        (.write *$out$* "\n")
                                        (.flush *$out$*)))
                                    nil)]
               (try
                 ;; Send :quit to exit the loop.
                 (when-not (identical? message :quit)
                   (try
                     (handle
                       (->
                         (merge options message)
                         ;; Set the value of the :reply key to a function that
                         ;; handlers can use to send a reply to the client.
                         (assoc :reply
                           (fn [response]
                             ;; Rebind standard output and to the original standard
                             ;; output.
                             ;;
                             ;; Without this, evaluation results would get
                             ;; double-wrapped like this:
                             ;;
                             ;; {:tag :out :val "{:tag :ret :val \"...\"}"}
                             (binding [*out* *$out$*]
                               ;; Print the response into the standard output.
                               (prn (merge {:id id} response)))))))
                     (catch Exception ex
                       (set! *e ex)
                       (binding [*out* *$out$*]
                         (prn {:id id
                               :tag :err
                               :val (pr-str (Throwable->map ex))}))))
                   ;; Return true to recur.
                   true)
                 (catch Exception _
                   ;; We'll ignore exception handling here and just exit the
                   ;; loop if anything goes wrong.
                   false)))]
         ;; Only recur if no exceptions were thrown within the loop.
         (when recur? (recur)))))))

;; Define a multimethod for handling different kinds of messages to our RPC
;; server.
(defmulti handle :op)

;; Define a handler for evaluation operations.
(defmethod handle :eval
  ;; Use user-supplied (or default) R, E, and P.
  [{:keys [read eval print reply code]}]
  (try
    (let [form (read code)
          ret (eval form)]
      (set! *3 *2)
      (set! *2 *1)
      (set! *1 ret)
      (reply {:tag :ret :val (with-out-str (print ret))}))
    (catch RuntimeException ex
      (reply {:tag :err :val (-> ex main/ex-triage main/ex-str)}))))

;; Fallback if the client sends an :op we don't recognize.
(defmethod handle :default
  [{:keys [op]}]
  (throw (ex-info "Not implemented" {:op op})))
```

All right! Let's give it a try:

```clojure
user=> (rpc)
{:id 1 :op :eval :code "(inc 1)"} ; Request
{:id 1 :tag :ret :val "2\n"} ; Response
```

Looks like it works! What about prints to standard output?

```clojure
{:id 2 :op :eval :code "(println :hello)"} ; Request
{:id 2 :tag :out :val ":hello\n"} ; Print output
{:id 2 :tag :ret :val "nil\n"} ; Response
```

Just what we wanted: we can easily distinguish between prints (`:out`) and evaluation results (`:ret`) so that the client can handle them differently. Additionally, every request and response has an `:id` so that we can easily match responses with requests on the client.

All right, the basics seem to work fine. What if we try something wild, like running a new REPL?

```clojure
{:id 3
 :op :eval
 :code "(clojure.main/repl :read clojure.core.server/repl-read)"} ; Request
{:id 3 :tag :out :val "user=> "} ; Print output
{:id 3 :tag :out :val "user=> "} ; Print output
```

Hmm. Not sure why the `user=>` prompt got printed twice, but let's just ignore that for now. Let's try evaluating something:

```clojure
{:id 5 :op :eval :code "(inc 1)"} ; Request
{:id 4 :tag :out :val "{:id 5, :op :eval, :code \"(inc 1)\"}\n"} ; Print output
{:id 4 :tag :out :val "user=> "} ; Print output
```

Oh, of course. Our RPC server expects envelopes (`{:id 1 :op :eval ...}`), but the REPL we ran within expects plain Clojure forms.

Let's try giving it one of those:

```clojure
(inc 1) ; Request
{:id 4 :tag :out :val "2\n"} ; Print output
{:id 4 :tag :out :val "user=> "} ; Print output
```

It worked otherwise, but now we're in a bit of a limbo: the input is not in an envelope, but the output is. Also, both evaluation results and prints have the `:out` tag.

Since we saved a reference to the original output stream, we can solve this problem. Let's first quit the REPL we're currently in:

```clojure
;; clojure.core.server/repl-read is a function that quits the current REPL if
;; you send it :repl/quit, so let's do that.
:repl/quit ; Request
{:id 3 :tag :ret :val "nil\n"} ; Response
```

Then, let's run a new REPL that prints all output into the original output stream we saved a reference to (`*$out$*`):

```clojure
{:id 6
 :op :eval
 :code "(binding [*out* my.rpc/*$out$*] (clojure.main/repl))"} ; Request
;; Double prompt again for some reason. Likely need to pass :prompt or
;; :need-prompt to clojure.main/repl to fix.
my.rpc=> my.rpc=> (inc 1) ; Request
2 ; Print output
my.rpc=> ; Print output
```

Voilà! We finally managed to escape the confines of the RPC envelope.

It did take a bit of work, though, and we made some assumptions along the way. For example, we assumed that the client using our RPC server knows when to send and receive framed messages and when not. We also assumed that it is acceptable that the client must be aware of `my.rpc/*$out$*` to escape message framing. Furthermore, we assumed that the client can either parse [EDN](https://github.com/edn-format/edn), or that the client and the server can talk using some other [shared language](https://nrepl.org/nrepl/1.1/design/transports.html#bencode-transport).

While the RPC protocol has many benefits, it is slightly more complex than a REPL. The REPL is almost brutal in its simplicity: put code in, get results out. Upgrade to a different protocol if you want. Switching from RPC to REPL is possible, but it's a bit more work than switching from REPL to RPC (or, indeed, switching from REPL to another type of REPL).

## Closing words

We've looked at each of the main ingredients of the REPL: read, evaluate, and print. (There's not much to say about loop: it just goes back to R.) We've seen how each of them works, what it means to customize them, and why you might want to do that. We've looked at what sets Lips REPLs apart from the interactive shells in other languages. Finally, we implemented an alternative to a REPL that has some of the same features as a REPL, but a slightly different set of tradeoffs.

What we haven't discussed is the tooling around REPLs. To my knowledge, the only Clojure programming environments that support REPLs are [inf-clojure](https://github.com/clojure-emacs/inf-clojure) for Emacs and [Tutkain](http://tutkain.flowthing.me), which is a tool I've made for myself. Unfortunately, Tutkain's support for REPLs is not great. That is partly because I haven't invested enough time to make it great, and partly because [Sublime Text doesn't expose an API](https://github.com/sublimehq/sublime_text/issues/6058) that would, I think, help make REPL interactions more fluent.

So, in the end, all I've probably done is waste your time talking about a tool you probably won't end up using anyway.

Perhaps, though, it helps spark some interest in exploring the possibilities of tooling where reading, evaluating, and printing are distinct, composable steps. You never know.
