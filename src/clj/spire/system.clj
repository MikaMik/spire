(ns spire.system
  (:require [clojure.java.shell :as shell])
  )

(def apt-env
  {"DEBIAN_FRONTEND" "noninteractive"})

(defmulti apt (fn [state & args] state))

(defmethod apt :update [_]
  (shell/sh "apt-get" "update" :env apt-env))

(defmethod apt :upgrade [_]
  (shell/sh "apt-get" "upgrade" "-y" :env apt-env))

(defmethod apt :dist-upgrade [_]
  (shell/sh "apt-get" "dist-upgrade" "-y" :env apt-env))

(defmethod apt :autoremove [_]
  (shell/sh "apt-get" "autoremove" "-y" :env apt-env))

(defmethod apt :clean [_]
  (shell/sh "apt-get" "clean" "-y" :env apt-env))

(defmethod apt :install [_ package-or-packages]
  (if (string? package-or-packages)
    (shell/sh "apt-get" "install" "-y" package-or-packages :env apt-env)
    (apply shell/sh "apt-get" "install" "-y" (concat package-or-packages [:env apt-env]))))

(defmethod apt :remove [_ package-or-packages]
  (if (string? package-or-packages)
    (shell/sh "apt-get" "remove" "-y" package-or-packages :env apt-env)
    (apply shell/sh "apt-get" "remove" "-y" (concat package-or-packages [:env apt-env]))))

(defmethod apt :purge [_ package-or-packages]
  (if (string? package-or-packages)
    (shell/sh "apt-get" "purge" "-y" package-or-packages :env apt-env)
    (apply shell/sh "apt-get" "purge" "-y" (concat package-or-packages [:env apt-env]))))

(defmethod apt :download [_ package-or-packages]
  (if (string? package-or-packages)
    (shell/sh "apt-get" "download" "-y" package-or-packages :env apt-env)
    (apply shell/sh "apt-get" "download" "-y" (concat package-or-packages [:env apt-env]))))

#_ (apt :download ["iputils-ping" "traceroute"])
#_ (apt :autoremove)

(defn hostname [hostname]
  (spit "/etc/hostname" hostname)
  (shell/sh "hostname" hostname))
