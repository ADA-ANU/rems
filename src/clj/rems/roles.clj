(ns rems.roles
  (:require [rems.context :as context]))

(defn has-roles?
  "Checks that the `context/*roles*` contains one of the given roles."
  [& roles]
  (assert (bound? #'context/*roles*) "context/*roles* is not defined")
  (boolean (some (set roles) context/*roles*)))

(defmacro when-roles
  "Executes the body when one of the given roles is present."
  [roles & body]
  `(when (has-roles? ~@roles)
     ~@body))

(defmacro when-role
  "Executes the body when the given role is present."
  [role & body]
  `(when-roles #{~role} ~@body))