(ns spire.module.apt-key
  (:require [spire.ssh :as ssh]
            [spire.facts :as facts]
            [spire.utils :as utils]
            [clojure.string :as string]
            ))

(defmulti make-script (fn [command opts] command))

(defmulti preflight (fn [command opts] command))

(defmulti process-result (fn [command opts result] command))

;;
;; (apt-key :list ...)
;;
(defmethod preflight :list [_ _]
  (facts/check-bins-present #{:sed :grep :awk :apt-key :curl :bash}))

(defmethod make-script :list [_ {:keys [repo filename]}]
  (utils/make-script
   "apt_key_list.sh"
   {}))

(defn process-key [out-lines]
  (loop [[line & remains] out-lines
         present-section nil
         data {}]
    (if line
      (if (re-find #"^\w" line)
        (let [[section body] (string/split line #"\s+" 2)]
          (recur remains (keyword section) (assoc data (keyword section) [body])))
        (recur remains present-section
               (update data present-section conj (string/trim line))))
      data)))

(defn process-apt-key-list-output [out-lines]
  (let [file-keys
        (loop [[line & remains] out-lines
               present-file nil
               lines {}]
          (if line
            (if (and remains (string/starts-with? (first remains) "-----"))
              (recur (rest remains) line lines)
              (recur remains present-file (update lines present-file #(conj (or %1 []) %2) line)))
            lines))]
    (->> (for [[k v] file-keys]
           [k
            (->> v
                 (partition-by empty?)
                 (filter #(not= % '("")))
                 (mapv process-key))])
         (into {}))))

(defmethod process-result :list
  [_ _ {:keys [out err exit] :as result}]
  (let [out-lines (string/split out #"\n")]
    (cond
      (zero? exit)
      (assoc result
             :result :ok
             :out-lines out-lines
             :keys (process-apt-key-list-output out-lines)
             )

      (= 255 exit)
      (assoc result
             :result :changed
             :out-lines out-lines
             )

      :else
      (assoc result
             :result :failed
             :out-lines out-lines))))




(utils/defmodule apt-key* [command opts]
  [host-config session {:keys [exec-fn shell-fn stdin-fn] :as shell-context}]
  (or
   (preflight command opts)
   (->>
    (exec-fn session (shell-fn "bash") (stdin-fn (make-script command opts)) "UTF-8" {})
    (process-result command opts))))

(defmacro apt-key
  "manage the presence or absence of extra apt repositories.
  (apt-repo command opts)

  given:

  `command`: Should be one of `:present` or `:absent`

  `opts`: a hashmap of options with the following keys:

  `:repo` The repository line as it appears in apt sources file, or a
  ppa description line.

  `:filename` an optional filename base for the storage of the repo
  definition inside /etc/apt/sources.d
  "
  [& args]
  `(utils/wrap-report ~&form (apt-key* ~@args)))

(def documentation
  {
   :module "apt-repo"
   :blurb "Manage extra apt repositories"
   :description
   [
    "This module manages the presence of extra apt repositories."]
   :form "(apt-repo command opts)"
   :args
   [{:arg "command"
     :desc "The overall command to execute. Should be one of `:present` or `:absent`"
     :values
     [[:present "Ensure the specified apt repository is present on the machine"]
      [:absent "Ensure the specified apt repository is absent on the machine"]]}
    {:arg "options"
     :desc "A hashmap of options"}]

   :opts
   [
    [:repo
     {:description ["The repository line as it appears in an apt source file."
                    "A ppa description string."]
      :type :string
      :required true}]
    [:filename
     {:description ["The base filename to use when storing the config."
                    "Only necessary when `command` is `:present`."
                    "When `command` is `:absent` the repo lists are searched and all references to the repo are removed."]}]
    ]

   :examples
   [
    {:description
     "Add specified repository into sources list using specified filename."
     :form "
(apt-repo :present {:repo \"deb http://dl.google.com/linux/chrome/deb/ stable main\"
                    :filename \"google-chrome\"})"}
    {:description
     "Install an ubuntu ppa apt source for php packages"
     :form "
(apt-repo :present {:repo \"ppa:ondrej/php\"})"}

    {:description
     "Remove the ubuntu php ppa"
     :form "
(apt-repo :absent {:repo \"ppa:ondrej/php\"})"}

    ]
   })
