(ns spire.module.sysctl
  (:require [spire.output :as output]
            [spire.state :as state]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
            [spire.utils :as utils]
            [clojure.string :as string]))

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defmulti make-script (fn [command opts] command))

(defmulti preflight (fn [command opts] command))

(defmulti process-result (fn [command opts result] command))

;;
;; (sysctl :present ...)
;;
(defmethod preflight :present [_ {:keys [name value reload file] :as opts}]
  nil)

(defmethod make-script :present [_ {:keys [value reload file] :as opts}]
  (utils/make-script
   "sysctl_present.sh"
   {:FILE (or file "/etc/sysctl.conf")
    :REGEX (format "^%s\\s*=" (:name opts))
    :NAME (:name opts)
    :VALUE (name value)
    :RELOAD (str reload)}))

(defmethod process-result :present
  [_ {:keys [name value reload file] :as opts} {:keys [out err exit] :as result}]
  (let [result (assoc result :out-lines (string/split out #"\n"))]
    (cond
      (zero? exit)
      (assoc result
             :exit 0
             :result :ok)

      (= 255 exit)
      (assoc result
             :exit 0
             :result :changed)

      :else
      (assoc result
             :result :failed))))


(utils/defmodule sysctl* [command {:keys [name value reload file] :as opts}]
  [host-string session]
  (or
   (preflight command opts)
   (->>
    (ssh/ssh-exec session (make-script command opts) "" "UTF-8" {})
    (process-result command opts)))

  )

(defmacro sysctl [& args]
  `(utils/wrap-report ~*file* ~&form (sysctl* ~@args)))
