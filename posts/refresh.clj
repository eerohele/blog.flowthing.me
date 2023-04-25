;; If you modify two namespaces which depend on each other, you must remember to reload them in the correct order to avoid compilation errors.
;;
;; Whenever you alter a definition, re-evaluate it. If you find that you don't remember what you've changed and need to re-evaluate entire namespaces at once, you've gone too long without reloading.

;; If you remove definitions from a source file and then reload it, those definitions are still available in memory. If other code depends on those definitions, it will continue to work but will break the next time you restart the JVM.
;;
;; If you find yourself doing this, you're breaking what should be a stable API. You deserve to feel the pain. If you absolutely must do something like this, use `ns-unmap`.
;;
;; If you have a definition in namespace A and you use it from namespaces B, C, and D,


;; Protocols
;;
;;

;; If the reloaded namespace contains defprotocol, you must also reload any records or types implementing that protocol and replace any existing instances of those records/types with new instances.


(ns pokémon)

(defprotocol Attacker
  (attack [this other move]))

(defprotocol Escaper
  (escape [this]))

(defrecord Pokémon
  [name moves]
  Attacker
  (attack [this other move]
    (format "%s makes a vicious attack on %s using %s!"
      (:name this)
      (:name other)
      (:name (moves move))))
  Escaper
  (escape [this]
    (format "%s bolts to safety!" (:name this))))

(def pikachu
  (Pokémon. "Pikachu"
    {:nuzzle {:name "Nuzzle"}}))

(def bulbasaur
  (Pokémon. "Bulbasaur"
    {:vine-whip {:name "Vine Whip"}}))

(attack bulbasaur pikachu :vine-whip)
(attack pikachu bulbasaur :nuzzle)
;; Changes to methods of a defrecord or deftype will not have any effect on existing instances of that type.
;; -- https://cognitect.com/blog/2013/06/04/clojure-workflow-reloaded
(escape pikachu)

;; Multimethods
;;
;; >If the reloaded namespace contains defmulti, you must also reload all of the associated defmethod expressions.

(defmulti handle (juxt :id :dialect))

(defmethod handle [:completions :clj]
  [{:keys [ns prefix] :or {ns 'clojure.core}}]
  (eduction
    (map key)
    (filter #(.startsWith (str %) prefix))
    (ns-publics ns)))

(defmethod handle [:completions :cljs]
  [_]
  ,,,)

(defmethod handle [:meta :clj]
  [{:keys [ns sym] :or {ns 'clojure.core}}]
  (meta (ns-resolve ns sym)))

(handle {:id :completions :dialect :clj :prefix "ma"})
(handle {:id :completions :dialect :cljs :prefix "ma"})
(handle {:id :meta :dialect :clj :sym 'mapcat})

; {
;     "keys": ["ctrl+p", "ctrl+-"],
;     "command": "tutkain_evaluate",
;     "args": {"code": "(ns-unmap *ns* '$0)", "scope": "form"},
;     "context": [{"key": "selector", "operator": "equal", "operand": "source.clojure"}]
; },

;; For example, the effect of a changed macro definition will not be seen until code which uses the macro has been recompiled.
;; -- https://cognitect.com/blog/2013/06/04/clojure-workflow-reloaded
(defmacro one
  []
  2)

(defn callsite
  []
  (one))

(callsite)