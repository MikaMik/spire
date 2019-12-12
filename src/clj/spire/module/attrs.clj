(ns spire.module.attrs
  (:require [spire.utils :as utils]
            [spire.ssh :as ssh]
            [clojure.java.io :as io])
  )

(defn make-script [{:keys [path owner group mode dir-mode attrs recurse]}]
  (utils/make-script
   "attrs.sh"
   {:FILE (some->> path utils/path-escape)
    :OWNER owner
    :GROUP group
    :MODE (if (number? mode) (format "%o" mode)  mode)
    :DIR_MODE (if (number? dir-mode) (format "%o" dir-mode)  dir-mode)
    :ATTRS attrs
    :RECURSE (if recurse "1" nil)}))

(defn set-attrs [session opts]
  (ssh/ssh-exec session (make-script opts) "" "UTF-8" {}))



#_
(make-script "p" "o" "g" "m" "a")
