(ns spire.module.copy
  (:require [spire.output :as output]
            [spire.ssh :as ssh]
            [spire.scp :as scp]
            [spire.utils :as utils]
            [spire.module.attrs :as attrs]
            [digest :as digest]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defn preflight [{:keys [src dest]}]
  (cond
    (not (and src dest))
    (assoc failed-result
           :exit 3
           :err ":src and :dest must both be specified")))

(defn process-result [{:keys [src dest owner group mode attrs] :as opts}
                      [copied? {:keys [out err exit] :as result}]]
  (cond
    (nil? result)
    {:result (if copied? :changed :ok)}

    (zero? exit)
    (assoc result
           :exit 0
           :result (if copied? :changed :ok))

    (= 255 exit)
    (assoc result
           :exit 0
           :result :changed)

    :else
    (assoc result
           :result :failed)))

(utils/defmodule copy [{:keys [src dest owner group mode attrs] :as opts}]
  [host-string session]
  (or
   (preflight opts)
   (->> (let [run (fn [command]
                    (let [{:keys [out exit]}
                          (ssh/ssh-exec session command "" "UTF-8" {})]
                      (when (zero? exit)
                        (string/trim out))))
              local-md5 (digest/md5 (io/as-file src))
              remote-md5 (some-> (run (format "%s -b \"%s\"" "md5sum" dest))
                                 (string/split #"\s+")
                                 first)]
          (let [copied?
                (if (= local-md5 remote-md5)
                  false
                  (do
                    (scp/scp-to session src dest :progress-fn (fn [& args] (output/print-progress host-string args)))
                    true))
                passed-attrs? (or owner group mode attrs)]
            (if (not passed-attrs?)
              ;; just copied
              [copied? nil]

              ;; run attrs
              [copied? (attrs/set-attrs
                        session
                        {:path dest
                         :owner owner
                         :group group
                         :mode mode
                         :attrs attrs})])))
        (process-result opts))))
