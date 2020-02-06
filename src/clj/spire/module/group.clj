(ns spire.module.group
  (:require [spire.utils :as utils]
            [spire.ssh :as ssh]
            [spire.output :as output]
            [clojure.string :as string]))

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defmulti make-script (fn [command opts] command))

(defmulti preflight (fn [command opts] command))

(defmulti process-result (fn [command opts result] command))

(defmethod preflight :present [_ {:keys [name gid] :as opts}]
  nil
  )

(defmethod make-script :present [_ {:keys [name gid password] :as opts}]
  (utils/make-script
   "group_present.sh"
   {:NAME name
    :GROUP_ID gid
    :PASSWORD password}))

(defmethod process-result :present
  [_ {:keys [user] :as opts} {:keys [out err exit] :as result}]
  (let [result (assoc result
                      :out-lines (string/split-lines out))]
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

(defmethod preflight :absent [_ {:keys [name gid system] :as opts}]
  nil
  )

(defmethod make-script :absent [_ {:keys [name] :as opts}]
  (utils/make-script
   "group_absent.sh"
   {:NAME name}))

(defmethod process-result :absent
  [_ {:keys [user] :as opts} {:keys [out err exit] :as result}]
  (let [result (assoc result
                      :out-lines (string/split-lines out))]
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

(utils/defmodule group* [command opts]
  [host-string session]
  (or
   (preflight command opts)
   (->>
    (ssh/ssh-exec session (make-script command opts) "" "UTF-8" {})
    (process-result command opts))))

(defmacro group [& args]
  `(utils/wrap-report ~*file* ~&form (group* ~@args)))
