---
title: seque
# description: WS, CSRF, XSS, SOP, CORS, CSP… WTF?
date: 2022-02-20
status: draft
---

There are clojure.core functions that don't see much use. One such function is `seque` (presumably pronounced "sea queue"). Let's see if we can't figure out what it's good for.

To get started, let's take a look at [the docstring for `clojure.core/seque`](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/seque). I've added some formatting and a couple of line breaks for clarity. The wording is original.

>Creates a queued seq on another (presumably lazy) seq `s`.
>
>The queued seq will produce a concrete seq in the background, and can get up to `n` items ahead of the consumer. `n-or-q` can be an integer `n` buffer size, or an instance of [`java.util.concurrent.BlockingQueue`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html).
>
>Note that reading from a seque can block if the reader gets ahead of the producer.

The docstring says that `seque` creates a "queued seq". To begin with, let's remind ourselves what a seq is. A [**seq**](https://clojure.org/reference/sequences) is an abstraction that lets us treat all kinds of collections as **sequences of elements**. The seq abstraction is what lets us call functions like `first` on a collection without having to think about the type of the collection.

```clojure
;; Map
(first {:a 1 :b 1})
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

The docstring of `seque` goes on to say that `seque` expects to receive a **lazy seq** as its argument. We already know what a seq is, but what is a *lazy* seq? A lazy seq is a seq that computes values when you actually need them, no sooner. The opposite of a lazy seq is a *concrete* seq. For example, `(range)` returns an infinite lazy seq of numbers. If it returned a *concrete* seq, evaluating this form would never return, because the call to `(range)` would compute numbers indefinitely:

```clojure
(take 10 (range))
;;=> (0 1 2 3 4 5 6 7 8 9)
```

A concrete seq, in turn, is one whose values are all already realized. For example, the vector `[1 2 3]` is a concrete seq, because we already know its each element. There is nothing left to compute.

The docstring says `seque` expects a *presumably lazy* seq. What does that mean? If the seq is not lazy, it is concrete. If it is concrete, there is no need to produce anything, because every element in the sequence has already been produced. It does not make sense to call `seque` on it. Therefore, `seque` is only useful if its argument contains elements that have not yet been computed.

<!-- The docstring says that `seque` creates a **queued seq**. What is a queued seq? -->

Now that we know that we should give `seque` a lazy seq and we know what one is, we can try using `seque`. First, let's try giving `seque` a lazy seq:

```clojure
;; Let's first tell Clojure to only print the first eight elements of a
;; collection. This is to avoid attempting to print a very long (or infinite)
;; collection of elements.
(set! *print-length* 8)

;; Call seque on a lazy seq
(seque (range 64))
;;=> (0 1 2 3 4 5 6 7 ...)
```

Not terribly exciting. We just get back the original range we gave to `seque`. It is also no faster than if we had just evaluated `(range 64)`, either:

```clojure
(time (range 64))
;;=> (0 1 2 3 4 5 6 7 ...)
;; Elapsed time: 0.048043 msecs

(time (seque (range 64)))
;;=> (0 1 2 3 4 5 6 7 ...)
;; Elapsed time: 0.095492 msecs
```

The reason we see no difference is that realizing a range this short takes very little time. Doing it in the background isn't useful.

Maybe we should try calling `seque` on a seq that takes longer to realize. To do that, let's first make a slow function. Here's `slow-inc`, a function that increments a number, but takes its sweet time doing it:

```clojure
(defn slow-inc
  "Increment a number, but slowly. Sensually, almost."
  [number]
  ;; Wait for a second before incrementing the number.
  (Thread/sleep 1000)
  ;; Increment the number, then return it to the caller.
  (inc number))

(time (slow-inc 1))
;;=> "Elapsed time: 1004.38591 msecs"
```

Cool! We can now use `slow-inc` to write a function that returns a lazy seq of integers:

```clojure
(defn lazy-nums
  "Return an infinite, lazy seq of positive integers."
  []
  (iterate slow-inc 1))

(time (doall (take 10 (lazy-nums))))
;;=> (1 2 3 4 5 6 7 8 9 10)
;; "Elapsed time: 9027.391625 msecs"
```

Oh yeah, nice and slow.

We now have a function that returns a lazy seq that yields elements at a rate of one per second. Now, let's see what happens if we give a lazy seq made by `lazy-nums` to `seque`.

```clojure
(do
  ;; Start a seque that produces at most five elements from its input before
  ;; stopping to wait for the consumer.
  (def seque-of-numbers (seque 5 (lazy-nums)))

  ;; Do something else while the seque produces items.
  (Thread/sleep 5000)

  ;; Take the first five elements from the seque.
  (time (doall (take 5 seque-of-numbers)))
  ;; "Elapsed time: 0.185669 msecs"
  ;;=> (1 2 3 4 5)
  ,,,)
```


```clojure
(def seque-of-numbers
  (seque 5 (lazy-nums)))

(time (doall (take 5 seque-of-numbers)))
;; "Elapsed time: 0.109622 msecs"
;;=> (1 2 3 4 5)
(time (doall (take 10 seque-of-numbers)))
;; "Elapsed time: 5006.433407 msecs"
;;=> (1 2 3 4 5 6 7 8 9 10)
(time (doall (take 20 seque-of-numbers)))
```

## Other resources

- https://www.youtube.com/watch?v=FyLK1lUeqRM
