---
title: "Clojure function explainers: seque"
# description: WS, CSRF, XSS, SOP, CORS, CSP… WTF?
date: 2022-02-20
---

Often the best way to come to understand something well is to explain it to others. In that vein, I figured I'd try my hand at writing about Clojure functions *I* want to understand better. One function I've always wanted to understand better is `seque` (presumably pronounced "sea-queue"). I'll start with that and see how it goes.

(In practice, this will probably just be a worse version of what [*Clojure: The Essential Reference*](https://www.manning.com/books/clojure-the-essential-reference) has on `seque`, but, you know, I don't have the book, so, uh... well, let's just give it a shot, shall we?)

To get started, let's take a look at the docstring for [`clojure.core/seque`](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/seque). I have added some formatting and a couple of line breaks for (subjective) clarity. It is otherwise original.

>Creates a queued seq on another (presumably lazy) seq `s`.
>
>The queued seq will produce a concrete seq in the background, and can get up to `n` items ahead of the consumer. `n-or-q` can be an integer `n` buffer size, or an instance of [`java.util.concurrent.BlockingQueue`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html).
>
>Note that reading from a seque can block if the reader gets ahead of the producer.

Before going further, let's remind ourselves what seq is. A **seq** is an abstraction that lets us treat all kinds of collections as **sequences of elements**. The seq abstraction is what lets us call `first`, for example, on all kinds of collections without worrying too much about their types.

```clojure
;; Hash map
(first {:a 1})
;;=> [:a 1]

;; Vector
(first [:a 1])
;;=> :a

;; List
(first '(:a 1))
;;=> :a

;; String
(first "abc")
;;=> \a

;; Persistent queue
(first (into clojure.lang.PersistentQueue/EMPTY [:a]))
;;=> :a

;; Java array
(first (into-array clojure.lang.Keyword [:a]))
;;=> :a
```

A *lazy* seq is just a seq that computes values when you actually need them, no sooner. The opposite of a lazy seq is a *concrete* seq. For example, `(range)` returns a lazy seq of numbers. If it returned a *concrete* seq, evaluating this would never return:

```clojure
(def infinite-seq-of-numbers (range))
(take 10 infinite-seq-of-numbers)
;;=> (0 1 2 3 4 5 6 7 8 9)
```

Returning to `seque`. Why the note in the docstring about a *presumably lazy* seq, then? Well, if the seq is not lazy, it is concrete. In that case, there is no need to produce anything, because everything has already been produced. Therefore, it does not make sense to call `seque` on it. In other words, `seque` is only useful if the input seq you give it is incrementally computed.

I think we're ready to try calling `seque`. Now that we know it only make sense to call `seque` on a lazy seq, let's try that first.

```clojure
;; Let's first tell Clojure to only print the first 16 elements of any
;; collection. This way we avoid attempting to print a very long (or infinite)
;; collection of elements.
(set! *print-length* 16)

(seque (range 64))
;;=> (0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 ...)
```

Not terribly exciting. We just get back the original range we gave to `seque`.

What did the docstring say again?

>The queued seq will produce a concrete seq in the background […]

All right, so `seque` turned our lazy range into a concrete range in the background — in another thread. Realizing a range is already very fast, though, so doing it in the background is not useful. Maybe we should try calling it on a seq that takes longer to realize.

```clojure
(defn slow-inc
  "Increment a number, but slowly. Sensually, almost."
  [number]
  ;; Wait for a second before incrementing the number.
  (Thread/sleep 1000)
  ;; Increment the number.
  (inc number))

(def numbers
  "An infinite sequence of numbers. But... slow."
  (iterate slow-inc 1))

(time (doall (map slow-inc (take 5 numbers))))
(time (doall (map slow-inc (seque (take 5 numbers)))))
;; Elapsed time: 4010.028124 msecs
⁣;;=> (1 2 3 4 5)
```