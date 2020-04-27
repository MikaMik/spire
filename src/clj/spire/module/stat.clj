(ns spire.module.stat
  (:require [spire.ssh :as ssh]
            [spire.utils :as utils]
            [spire.facts :as facts]
            [clojure.string :as string]
            [clojure.edn :as edn])
  (:import [java.util Date]))

(defn preflight [path]
  (facts/check-bins-present #{:stat}))

(defn make-script [path]
  (str "stat -c '%a\t%b\t%B\t%d\t%f\t%F\t%g\t%G\t%h\t%i\t%m\t%n\t%N\t%o\t%s\t%t\t%T\t%u\t%U\t%W\t%X\t%Y\t%Z\t%F' " (utils/path-quote path)))

(defn make-script-bsd [path]
  (str "stat -f '%Lp%t%d%t%i%t%l%t%u%t%g%t%r%t%a%t%m%t%c%t%B%t%z%t%b%t%k%t%f%t%v%t%HT%t%N%t%Y%t%Hr%t%Lr' " (utils/path-quote path)))

(defn- epoch-string->inst [s]
  (-> s Integer/parseInt (* 1000) Date.))

(def bsd-file-types
  {
   "Directory" :directory
   "Block Device" :block-device
   "Character Device" :char-device
   "Symbolic Link" :symlink
   "Fifo File" :fifo
   "Regular File" :regular-file
   "Socket" :socket
   })

(defn split-and-process-out-bsd [out]
  (let [parts (-> out string/trim (string/split #"\t"))
        [mode device inode nlink uid gid rdev
         atime mtime ctime btime filesize blocks
         blksize flags gen file-type link-source link-dest
         device-major device-minor] parts
        file-type (bsd-file-types file-type)
        result {:mode (Integer/parseInt mode 8)
                :device (edn/read-string device)
                :inode (Integer/parseInt inode)
                :nlink (Integer/parseInt nlink)
                :uid (Integer/parseInt uid)
                :gid (Integer/parseInt gid)
                :rdev (edn/read-string rdev)
                :atime (epoch-string->inst atime)
                :mtime (epoch-string->inst mtime)
                :ctime (epoch-string->inst ctime)
                :btime (epoch-string->inst btime)
                :size (Integer/parseInt filesize)
                :blocks (Integer/parseInt blocks)
                :blksize (Integer/parseInt blksize)
                :flags (Integer/parseInt flags)
                :gen (Integer/parseInt gen)
                :file-type file-type
                :device-major (edn/read-string device-major)
                :device-minor (edn/read-string device-minor)}]
    (if (= file-type :symlink)
      (assoc result
             :link-source link-source
             :link-dest link-dest)
      result)
    )
  )

(defn process-quoted-filename [quoted-line]
  (loop [[c & r] quoted-line
         acc ""]
    (case c
      \' (let [[r acc] (loop [[c & r] r
                              acc acc]
                         (assert c "ran out of chars while looking for close quote")
                         (case c
                           \' [r acc]
                           (recur r (str acc c))))]
           (recur r acc))
      \\ (recur (rest r) (str acc (first r)))
      \space [acc (apply str (conj r c))]
      nil (if c
            (recur r (str acc c))
            [acc nil])
      (assert false
              (apply str "a non valid character was found outside a quoted region: " c r))
      )))

(defn process-quoted-symlink-line [quoted-line]
  (let [[source remain] (process-quoted-filename quoted-line)]
    (if remain
      (do
        (assert (string/starts-with? remain " -> ") "malformed symlink line")
        (let [[dest remain] (process-quoted-filename (subs remain 4))]
          (assert (nil? remain) "malformed symlink line tail")
          {:source source
           :dest dest}))
      {:source source})))

#_ (process-quoted-symlink-line "'spire/spire-link'\\''f' -> 'foo'")

#_ (assert
    (= (process-quoted-filename "'spire/spire-link'\\''f' -> 'foo'")
       (process-quoted-filename "'spire/spire-link'\\''f'")
       "spire/spire-link'f"))

(def linux-file-types
  {
   "directory" :directory
   "block special file" :block-device
   "character special file" :char-device
   "symbolic link" :symlink
   "fifo" :fifo
   "regular file" :regular-file
   "socket" :socket
   })

(defn split-and-process-out [out]
  (let [[mode blocks blksize device raw-mode file-type
         gid group nlink inode mount-point
         file-name quoted-file-name optimal-io size
         device-major device-minor uid user ctime
         atime mtime stime file-type] (string/split (string/trim out) #"\t")
        {:keys [source dest]} (process-quoted-symlink-line quoted-file-name)
        file-type (linux-file-types file-type)
        result {:mode (Integer/parseInt mode 8)
                :device (Integer/parseInt device)
                :inode (Integer/parseInt inode)
                :nlink (Integer/parseInt nlink)
                :uid (Integer/parseInt uid)
                :user user
                :gid (Integer/parseInt gid)
                :group group
                :rdev nil
                :ctime (when-not (= "0" ctime)
                         (epoch-string->inst ctime)) ;; on linux 0 means "unknown"
                :atime (epoch-string->inst atime)
                :mtime (epoch-string->inst mtime)
                :btime nil
                :size (Integer/parseInt size)
                :blocks (Integer/parseInt blocks)
                :blksize (Integer/parseInt blksize)
                :flags nil
                :gen nil
                :file-type file-type
                :device-major (Integer/parseInt device-major)
                :device-minor (Integer/parseInt device-minor)}]
    (assert (= file-name source)
            (str "decoding of quoted stat filename mismatched: "
                 (prn-str file-name source)))

    (if (= file-type :symlink)
      (assoc result
             :link-source source
             :link-dest dest)
      result)))

(defn process-result [path {:keys [out err exit] :as result}]
  (cond
    (zero? exit)
    {:exit 0
     :err err
     :stat (facts/on-os :linux (split-and-process-out out)
                        :else (split-and-process-out-bsd out))
     :result :ok}

    :else
    (assoc result
           :result :failed
           :out-lines (string/split out #"\n")
           :err-lines (string/split err #"\n"))))

(utils/defmodule stat* [path]
  [host-config session {:keys [exec-fn shell-fn stdin-fn] :as shell-context}]
  (let [script (facts/on-os :linux (make-script path)
                            :else (make-script-bsd path))]
    (or
     (preflight path)

     ;; sash (reported as sh) does not correctly return its exit code
     ;; $ sash -c "echo foo; exit 1"; echo $?
     ;; foo
     ;; 0
     ;; so we invoke bash in this case as a work around
     (->> (exec-fn session
                        (shell-fn (facts/on-shell
                                   :sh "bash"
                                   :else script))
                        (stdin-fn (facts/on-shell
                                   :sh script
                                   :else ""))
                        "UTF-8" {})
          (process-result path)))))

(defmacro stat [& args]
  `(utils/wrap-report ~&form (stat* ~@args)))

;;
;; Test mode flags
;;
(defn other-exec? [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 1)))

(defn other-write? [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 2)))

(defn other-read? [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 4)))

(defn group-exec? [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 8)))

(defn group-write? [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 16)))

(defn group-read? [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 32)))

(defn user-exec? [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 64)))

(defn user-write? [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 128)))

(defn user-read? [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 256)))

(defn mode-flags [result]
  {:user-read? (user-read? result)
   :user-write? (user-write? result)
   :user-exec? (user-exec? result)
   :group-read? (group-read? result)
   :group-write? (group-write? result)
   :group-exec? (group-exec? result)
   :other-read? (other-read? result)
   :other-write? (other-write? result)
   :other-exec? (other-exec? result)})

(defn exec? [{{:keys [mode uid gid]} :stat}]
  (let [{{fact-uid :id} :uid group-ids :group-ids} (facts/get-fact [:user])]
    (if (zero? fact-uid)
      ;; root can execute any file that has any executable bit set
      (pos? (bit-and mode (bit-or 64 8 1)))

      ;; check individual flags
      (or
       (and (= fact-uid uid) (pos? (bit-and mode 64)))
       (and (group-ids gid) (pos? (bit-and mode 8)))
       (pos? (bit-and mode 1))))))

(defn readable? [{{:keys [mode uid gid]} :stat}]
  (let [{{fact-uid :id} :uid group-ids :group-ids} (facts/get-fact [:user])]
    (if (zero? fact-uid)
      ;; root can read any file
      true

      ;; check individual flags
      (or
       (and (= fact-uid uid) (pos? (bit-and mode 256)))
       (and (group-ids gid) (pos? (bit-and mode 32)))
       (pos? (bit-and mode 4))))))

(defn writeable? [{{:keys [mode uid gid]} :stat}]
  (let [{{fact-uid :id} :uid group-ids :group-ids} (facts/get-fact [:user])]
    (if (zero? fact-uid)
      ;; root can write to any file
      true

      ;; check individual flags
      (or
       (and (= fact-uid uid) (pos? (bit-and mode 128)))
       (and (group-ids gid) (pos? (bit-and mode 16)))
       (pos? (bit-and mode 2))))))

;;
;; File types
;;
(defn directory? [{{:keys [file-type]} :stat}]
  (= :directory file-type))

(defn block-device? [{{:keys [file-type]} :stat}]
  (= :block-device file-type))

(defn char-device? [{{:keys [file-type]} :stat}]
  (= :char-device file-type))

(defn symlink? [{{:keys [file-type]} :stat}]
  (= :symlink file-type))

(defn fifo? [{{:keys [file-type]} :stat}]
  (= :fifo file-type))

(defn regular-file? [{{:keys [file-type]} :stat}]
  (= :regular file-type))

(defn socket? [{{:keys [file-type]} :stat}]
  (= :socket file-type))


(def documentation
  {
   :module "stat"
   :blurb "Retrieve file status"
   :description
   ["This module runs the stat command on files or directories."]
   :form "(stat path)"
   :args
   [{:arg "path"
     :desc "The path of the file or directory to stat."}]

   :opts []

   :examples
   [
    {:description
     "Stat a file."
     :form "
(stat \"/etc/resolv.conf\")"}

    {:description
     "Test the return value of a stat call for a unix domain socket"
     :form "
(socket? (stat (System/getenv \"SSH_AUTH_SOCK\")))
"}]})
