(ns spire.system
  (:require [clojure.java.shell :as shell]
            [clojure.string :as string]
            [spire.transport :as transport]
            )
  )

(def apt-command "DEBIAN_FRONTEND=noninteractive apt-get")

(defn apt-get [& args]
  (transport/sh (string/join " " (concat [apt-command] args)) "" ""))

(defn process-values [result func]
  (->> result
       (map (fn [[k v]] [k (func v)]))
       (into {})))

(defn process-apt-update-line [line]
  (let [[method remain] (string/split line #":" 2)]
    [method remain]
    )
  )

(defmulti apt* (fn [state & args] state))

(defmethod apt* :update [_]
  (-> (apt-get "update")
      (process-values
       (fn [{:keys [exit out] :as result}]
         (if (zero? exit)
           (assoc result
                  :result :ok
                  :out-lines (string/split out #"\n")
                  :data (-> out
                            (string/split #"\n")
                            (->> (map process-apt-update-line))
                            )
                  )
           (assoc result :result :fail))))))

(defmethod apt* :upgrade [_]
  (apt-get "upgrade" "-y"))

(defmethod apt* :dist-upgrade [_]
  (apt-get "dist-upgrade" "-y"))

(defmethod apt* :autoremove [_]
  (apt-get "autoremove" "-y"))

(defmethod apt* :clean [_]
  (apt-get "clean" "-y"))

(defmethod apt* :install [_ package-or-packages]
  (if (string? package-or-packages)
    (apt-get "install" "-y" package-or-packages)
    (apt-get "install" "-y" (string/join " " package-or-packages))))

(defmethod apt* :remove [_ package-or-packages]
  (if (string? package-or-packages)
    (apt-get "remove" "-y" package-or-packages)
    (apt-get "remove" "-y" (string/join " " package-or-packages))))

(defmethod apt* :purge [_ package-or-packages]
  (if (string? package-or-packages)
    (apt-get "purge" "-y" package-or-packages)
    (apt-get "purge" "-y" (string/join " " package-or-packages))))

(defmethod apt* :download [_ package-or-packages]
  (if (string? package-or-packages)
    (apt-get "download" "-y" package-or-packages)
    (apt-get "download" "-y" (string/join " " package-or-packages))))

(defn apt [& args]
  (pr (concat '(apt) args))
  (.flush *out*)
  (apply apt* args))


#_ (apt* :download ["iputils-ping" "traceroute"])
#_ (apt* :autoremove)

(defn hostname [hostname]
  (spit "/etc/hostname" hostname)
  (shell/sh "hostname" hostname))
