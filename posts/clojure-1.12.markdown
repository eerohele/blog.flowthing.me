---
title: New Java interoperability features in Clojure 1.12
date: 2024-04-29
description: TODO
status: draft
---

[Clojure 1.12](https://clojure.org/news/2024/04/28/clojure-1-12-alpha10), solves a set of [Java interoperability](https://clojure.org/reference/java_interop) problems in earlier Clojure versions. In this article, I'll show you some examples of some of the new features.

## Method values

You can now use [Java instance methods, static methods, and constructors](https://docs.oracle.com/javase/tutorial/java/javaOO/methods.html) as **values**. The main use case of this feature is [higher-order functions](https://clojure.org/guides/higher_order_functions). Let's look at an example of each.

### Instance methods

Instead of this:

```clojure
user=> (map #(.toUpperCase %) ["foo" "bar"])
("FOO" "BAR")
```

You can do this:

```clojure
user=> (map String/.toUpperCase ["foo" "bar"])
("FOO" "BAR")
```

This feature has two main benefits. First, the syntax is less busy. The difference is especially noticeable in pipelines like this:

```clojure
user=> (map (comp first String/.toUpperCase) ["foo" "bar"])
(\F \B)

;; vs.

user=> (map (comp first #(.toUpperCase %)) ["foo" "bar"])
(\F \B)
```

Second, when you use it, Clojure does not not need to [reflect](https://clojure.org/reference/java_interop#typehints) at runtime as often as with the old syntax.

```clojure
user=> (set! *warn-on-reflection* true)
true
;; Clojure needs to reflect to figure out the class whose .toUpperCase method
;; to call.
user=> (map #(.toUpperCase %) ["foo" "bar"])
Reflection warning, NO_SOURCE_FILE:1:1 - reference to field toUpperCase can't be resolved.
("FOO" "BAR")
;; No reflection needed: the "String/" qualifier names the target class.
user=> (map String/.toUpperCase ["foo" "bar"])
("FOO" "BAR")
```

### Static methods

Given:

```clojure
user=> (import '(java.time Instant))
java.time.Instant
user=> (def date-strings ["2007-12-03T10:15:30.00Z" "2009-05-04T15:58:43.00Z"])
#'user/date-strings
```

Instead of this:

```clojure
user=> (map #(Instant/parse %) date-strings)
(#object[java.time.Instant 0x6cd830da "2007-12-03T10:15:30Z"]
 #object[java.time.Instant 0x2a484d12 "2009-05-04T15:58:43Z"])
```

You can do this:

```clojure
user=> (map Instant/parse date-strings)
(#object[java.time.Instant 0x6cd830da "2007-12-03T10:15:30Z"]
 #object[java.time.Instant 0x2a484d12 "2009-05-04T15:58:43Z"])
```

You no longer need to wrap Instant/parse in an anonymous function.

### Constructors

Like [static methods](#static-methods):

```clojure
(map String/new [(byte-array [0xCA 0xFE])])
```

### Other uses

Since Java methods are now [values](https://www.youtube.com/watch?v=-6BsiVyC1kM), you can also do things like pass them as arguments to other functions. For example:

```clojure
```
