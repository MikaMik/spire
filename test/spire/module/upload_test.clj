(ns spire.module.upload-test
  (:require [clojure.test :refer :all]
            [spire.test-config :as test-config]
            [clojure.string :as string]
            [clojure.java.shell :as shell]
            [spire.module.upload :refer :all]
            [spire.module.attrs :refer :all]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
            [spire.state :as state]
            [spire.config :as config]
            [clojure.java.io :as io]
            [spire.test-utils :as test-utils]))

(clojure.lang.RT/loadLibrary "spire")

(defn no-scp [& args]
  (assert false "second copy should be skipped"))

(deftest upload-test
  (testing "upload test"
    (test-utils/with-temp-file-names [tf tf2 tf3 tf4]
      (transport/ssh
       test-config/localhost
       ;; copy file
       (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
              (upload {:src "test/files/copy/test.txt" :dest tf})))
       (is (test-utils/files-md5-match? "test/files/copy/test.txt" tf))

       (with-redefs [spire.scp/scp-to no-scp]
         ;; second copy doesn't transfer files
         (is (= {:result :ok, :attr-result {:result :ok}, :copy-result {:result :ok}}
                (upload {:src "test/files/copy/test.txt" :dest tf})))
         (is (test-utils/files-md5-match? "test/files/copy/test.txt" tf))

         ;; reupload changed file modes with :changed
         (is (= {:result :changed, :attr-result {:result :changed} :copy-result {:result :ok}}
                (upload {:src "test/files/copy/test.txt" :dest tf :mode 0777})))
         (is (= "777" (test-utils/mode tf)))

         ;; and repeat doesn't change anything
         (is (= {:result :ok, :attr-result {:result :ok} :copy-result {:result :ok}}
                (upload {:src "test/files/copy/test.txt" :dest tf :mode 0777})))
         (is (= "777" (test-utils/mode tf))))

       ;; try and copy directory without recurse
       (test-utils/should-ex-data
        {:exit 3
         :out ""
         :err ":recurse must be true when :src specifies a directory."
         :result :failed}
        (upload {:src "test/files" :dest tf}))

       ;; try and copy directory over existing file without :force
       (test-utils/should-ex-data
        {:result :failed,
                  :attr-result {:result :ok},
                  :copy-result {:result :failed, :err "Cannot copy `content` directory over `dest`: destination is a file. Use :force to delete destination file and replace."}}
        (upload {:src "test/files" :dest tf :recurse true}))

       ;; force copy dir over file
       (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
              (upload {:src "test/files" :dest tf :recurse true :force true :mode 0777})))
       (is (test-utils/recurse-file-size-type-name-match? "test/files" tf))
       (is (test-utils/find-files-md5-match? "test/files" tf))

       ;; recopy dir but with :preserve
       (with-redefs [spire.scp/scp-to no-scp]
         (is (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :ok}}
                (upload {:src "test/files" :dest tf :recurse true :force true :preserve true})))
         (is (test-utils/recurse-file-size-type-name-mode-match? "test/files" tf))
         ;; (is (test-utils/recurse-access-modified-match? "test/files" tf))
         )

       ;; preserve copy from scratch
       (let [result (upload {:src "test/files" :dest tf2 :recurse true :preserve true})]
         (is
          (or
           (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
              result)
           (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :changed}}
              result)
           )))

       (is (test-utils/recurse-file-size-type-name-mode-match? "test/files" tf2))
       ;; (is (test-utils/recurse-access-modified-match? "test/files" tf2))

       ;; mode and dir-mode from scratch
       (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
              (upload {:src "test/files" :dest tf3 :recurse true :mode 0666 :dir-mode 0777})))
       (is (test-utils/recurse-file-size-type-name-match? "test/files" tf3))
       (is (test-utils/find-local-files-mode-is? tf3 "666"))
       (is (test-utils/find-local-dirs-mode-is? tf3 "777"))

       (with-redefs [spire.scp/scp-to no-scp]
         ;; redo copy but change mode and dir-mode
         (is (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :ok}}
                (upload {:src "test/files" :dest tf3 :recurse true :mode 0644 :dir-mode 0755})))
         (is (test-utils/recurse-file-size-type-name-match? "test/files" tf3))
         (is (test-utils/find-local-files-mode-is? tf3 "644"))
         (is (test-utils/find-local-dirs-mode-is? tf3 "755")))

       ;; copy with unexecutable directory
       (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
              (upload {:src "test/files" :dest tf4 :recurse true :mode 0 :dir-mode 0})))
       ;; will need root just to check this directory
       (transport/ssh
        test-config/localhost-root
        (is (test-utils/recurse-file-size-type-name-match? "test/files" tf4))
        (is (test-utils/find-remote-files-mode-is? tf4 "0"))
        (is (test-utils/find-remote-dirs-mode-is? tf4 "0"))

        ;; the with-temp-file-names macro wont be able to delete this, so lets do it now while we are root
        ;; TODO: when implementing testing on another target system, the with-temp-file-names could
        ;; be made to do remote deletion
        (test-utils/ssh-run (format "rm -rf \"%s\"" tf4))))))
  )
