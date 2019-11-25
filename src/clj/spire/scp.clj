(ns spire.scp
  (:require [spire.ssh :as ssh]
            [clojure.java.io :as io]
            [clojure.string :as string])
  (:import [java.io InputStream OutputStream File
            PipedInputStream PipedOutputStream]
           [com.jcraft.jsch ChannelExec]))

;; https://web.archive.org/web/20170215184048/https://blogs.oracle.com/janp/entry/how_the_scp_protocol_works

(def debug println)
(def debugf (comp println format))

(defn- scp-send-ack
  "Send acknowledgement to the specified output stream"
  [^OutputStream out]
  (.write out (byte-array [0]))
  (.flush out))

(defn- scp-receive-ack
  "Check for an acknowledgement byte from the given input stream"
  [^InputStream in]
  (let [code (.read in)]
    (assert (zero? code) "scp protocol error")))

(defn- scp-send-command
  "Send command to the specified output stream"
  [^OutputStream out ^InputStream in ^String cmd-string]
  (.write out (.getBytes (str cmd-string "\n")))
  (.flush out)
  (debugf "Sent command %s" cmd-string)
  (scp-receive-ack in)
  (debug "Received ACK"))

(defn- scp-copy-file
  "Send acknowledgement to the specified output stream"
  [^OutputStream send ^InputStream recv ^File file
   {:keys [mode buffer-size preserve]
    :or {mode 0644 buffer-size 1024 preserve false}}]

  (when preserve
    (scp-send-command
     send recv
     (format "P%d 0 %d 0" (.lastModified file) (.lastModified file))))
  (scp-send-command
   send recv
   (format "C%04o %d %s" mode (.length file) (.getName file)))
  (debugf "Sending %s" (.getAbsolutePath file))
  (io/copy file send :buffer-size buffer-size)
  (scp-send-ack send)
  (debug "Receiving ACK after send")
  (scp-receive-ack recv))

(defn- scp-copy-dir
  "Send acknowledgement to the specified output stream"
  [send recv ^File dir {:keys [dir-mode] :or {dir-mode 0755} :as options}]
  (debugf "Sending directory %s" (.getAbsolutePath dir))
  (scp-send-command
   send recv
   (format "D%04o 0 %s" dir-mode (.getName dir)))
  (doseq [^File file (.listFiles dir)]
    (cond
     (.isFile file) (scp-copy-file send recv file options)
     (.isDirectory file) (scp-copy-dir send recv file options)))
  (scp-send-command send recv "E"))

(defn- scp-files
  [paths recursive]
  (let [f (if recursive
            #(File. ^String %)
            (fn [^String path]
              (let [file (File. path)]
                (when (.isDirectory file)
                  (throw
                   (ex-info
                    (format
                     "Copy of dir %s requested without recursive flag" path)
                    {:type :clj-ssh/scp-directory-copy-requested})))
                file)))]
    (map f paths)))

(defn scp-to
  "Copy local path(s) to remote path via scp.
   Options are:
   :username   username to use for authentication
   :password   password to use for authentication
   :port       port to use if no session specified
   :mode       mode, as a 4 digit octal number (default 0644)
   :dir-mode   directory mode, as a 4 digit octal number (default 0755)
   :recursive  flag for recursive operation
   :preserve   flag for preserving mode, mtime and atime. atime is not available
               in java, so is set to mtime. mode is not readable in java."
  [session local-paths remote-path
   & {:keys [username password port mode dir-mode recursive preserve] :as opts}]
  (let [local-paths (if (sequential? local-paths) local-paths [local-paths])
        files (scp-files local-paths recursive)]
    (let [[^PipedInputStream in
           ^PipedOutputStream send] (ssh/streams-for-in)
          cmd (format "scp %s %s -t %s" (:remote-flags opts "") (if recursive "-r" "") remote-path)
          _ (debugf "scp-to: %s" cmd)
          {:keys [^PipedInputStream out-stream]}
          (ssh/ssh-exec session cmd in :stream opts)
          recv out-stream]
      (debugf
       "scp-to %s %s" (string/join " " local-paths) remote-path)
      (debug "Receive initial ACK")
      (scp-receive-ack recv)
      (doseq [^File file files]
        (debugf "scp-to: from %s" (.getPath file))
        (if (.isDirectory file)
          (scp-copy-dir send recv file opts)
          (scp-copy-file send recv file opts)))
      (debug "Closing streams")
      (.close send)
      (.close recv)
      nil)))
