(ns spire.namespaces
  (:require [spire.ssh :as ssh]
            [spire.utils :as utils]
            [spire.transfer :as transfer]
            [spire.transport :as transport]
            [spire.state :as state]
            [spire.output :as output]
            [spire.facts :as facts]
            [spire.selmer :as selmer]
            [spire.module.line-in-file :as line-in-file]
            [spire.module.get-file :as get-file]
            [spire.module.download :as download]
            [spire.module.upload :as upload]
            [spire.module.user :as user]
            [spire.module.apt :as apt]
            [spire.module.apt-repo :as apt-repo]
            [spire.module.pkg :as pkg]
            [spire.module.group :as group]
            [spire.module.shell :as shell]
            [spire.module.sysctl :as sysctl]
            [spire.module.service :as service]
            [spire.module.authorized-keys :as authorized-keys]
            [clojure.tools.cli]
            [sci.core :as sci]
            )
  )

(defn binding*
  "This macro only works with symbols that evaluate to vars themselves. See `*in*` and `*out*` below."
  [_ _ bindings & body]
  `(do
     (let []
       (push-thread-bindings (hash-map ~@bindings))
       (try
         ~@body
         (finally
           (pop-thread-bindings))))))

(def bindings
  {'apt* apt/apt*
   'apt (with-meta @#'apt/apt {:sci/macro true})
   'apt-repo* apt-repo/apt-repo*
   'apt-repo (with-meta @#'apt-repo/apt-repo {:sci/macro true})
   'pkg* pkg/pkg*
   'pkg (with-meta @#'pkg/pkg {:sci/macro true})
   ;;'hostname system/hostname
   'line-in-file* line-in-file/line-in-file*
   'line-in-file (with-meta @#'line-in-file/line-in-file {:sci/macro true})
   ;;'copy copy/copy
   'upload* upload/upload*
   'upload (with-meta @#'upload/upload {:sci/macro true})

   'user* user/user*
   'user (with-meta @#'user/user {:sci/macro true})
   'gecos user/gecos

   'get-fact facts/get-fact
   'fetch-facts facts/fetch-facts

   'get-file* get-file/get-file*
   'get-file (with-meta @#'get-file/get-file {:sci/macro true})

   'sysctl* sysctl/sysctl*
   'sysctl (with-meta @#'sysctl/sysctl {:sci/macro true})
   'service* service/service*
   'service (with-meta @#'service/service {:sci/macro true})

   'group* group/group*
   'group (with-meta @#'group/group {:sci/macro true})

   'selmer selmer/selmer

   'download* download/download*
   'download (with-meta @#'download/download {:sci/macro true})
   'authorized-keys* authorized-keys/authorized-keys*
   'authorized-keys (with-meta @#'authorized-keys/authorized-keys {:sci/macro true})

   'slurp slurp

   'shell* shell/shell*
   'shell (with-meta @#'shell/shell {:sci/macro true})

   ;;'ln (system/ln
   ;;'mkdir system/mkdir

   ;;'git vcs/git

   ;;'copy transfer/copy
   ;;'template transfer/template

   'ssh (with-meta @#'transport/ssh {:sci/macro true})
   'ssh-group (with-meta @#'transport/ssh-group {:sci/macro true})

   'on-os (with-meta @#'facts/on-os {:sci/macro true})
   'on-shell (with-meta @#'facts/on-shell {:sci/macro true})
   'on-distro (with-meta @#'facts/on-distro {:sci/macro true})

   'binding (with-meta @#'clojure.core/binding {:sci/macro true})

   'changed? utils/changed?

   ;; '*command-line-args* (sci/new-dynamic-var '*command-line-args* *command-line-args*)
   '*in* (sci/new-dynamic-var '*in* *in*)
   '*out* (sci/new-dynamic-var '*out* *out*)
   '*err* (sci/new-dynamic-var '*err* *err*)
   })

(def namespaces
  {
   'spire.transfer {'ssh (with-meta @#'transfer/ssh {:sci/macro true})}
   'clojure.core {'binding (with-meta binding* {:sci/macro true})
                  'push-thread-bindings clojure.core/push-thread-bindings
                  'pop-thread-bindings clojure.core/pop-thread-bindings
                  ;;                  'var (with-meta @#'clojure.core/var {:sci/macro true})
                  'println println
                  'prn prn
                  'pr pr

                  'future (with-meta @#'clojure.core/future {:sci/macro true})
                  'future-call clojure.core/future-call

                  }
   'clojure.set {'intersection clojure.set/intersection
                 }
   'spire.transport {'connect transport/connect
                     'disconnect transport/disconnect
                     'open-connection transport/open-connection
                     'close-connection transport/close-connection
                     'get-connection transport/get-connection
                     'flush-out transport/flush-out
                     'safe-deref transport/safe-deref
                     }
   'spire.ssh {'host-config-to-string ssh/host-config-to-string
               'host-config-to-connection-key ssh/host-config-to-connection-key
               'host-description-to-host-config ssh/host-description-to-host-config
               }
   'spire.utils {'colour utils/colour
                 'defmodule (with-meta @#'utils/defmodule {:sci/macro true})
                 'wrap-report (with-meta @#'utils/wrap-report {:sci/macro true})

                 }
   'spire.facts {'get-fact facts/get-fact}
   'spire.state {
                 '*host-config* (sci/new-dynamic-var 'state/*host-config* state/*host-config*)
                 '*connection* (sci/new-dynamic-var 'state/*connection* state/*host-config*)
                 'ssh-connections state/ssh-connections
                 'get-host-config state/get-host-config
                 }
   'spire.output {
                  'print-form output/print-form
                  'print-result output/print-result
                  }

   'clojure.java.io {'file clojure.java.io/file
                     }

   'clojure.tools.cli {
                       'cli clojure.tools.cli/cli
                       'make-summary-part clojure.tools.cli/make-summary-part
                       'format-lines clojure.tools.cli/format-lines
                       'summarize clojure.tools.cli/summarize
                       'get-default-options clojure.tools.cli/get-default-options
                       'parse-opts clojure.tools.cli/parse-opts
                       }

   'clojure.string {
                    'trim clojure.string/trim
                    }

   ;; modules
   'spire.module.apt {'apt* apt/apt*
                      'apt (with-meta @#'apt/apt {:sci/macro true})}
   'spire.module.authorized-keys {'authorized-keys* authorized-keys/authorized-keys*
                                  'authorized-keys (with-meta @#'authorized-keys/authorized-keys {:sci/macro true})}
   'spire.module.apt-repo {'apt-repo* apt-repo/apt-repo*
                           'apt-repo (with-meta @#'apt-repo/apt-repo {:sci/macro true})}
   'spire.module.group {'group* group/group*
                        'group (with-meta @#'group/group {:sci/macro true})}
   'spire.module.get-file {'get-file* get-file/get-file*
                           'get-file (with-meta @#'get-file/get-file {:sci/macro true})}
   'spire.module.line-in-file {'line-in-file* line-in-file/line-in-file*
                               'line-in-file (with-meta @#'line-in-file/line-in-file {:sci/macro true})}
   'spire.module.pkg {'pkg* pkg/pkg*
                      'pkg (with-meta @#'pkg/pkg {:sci/macro true})}
   'spire.module.service {'service* service/service*
                          'service (with-meta @#'service/service {:sci/macro true})}
   'spire.module.shell {'shell* shell/shell*
                        'shell (with-meta @#'shell/shell {:sci/macro true})}
   'spire.module.sysctl {'sysctl* sysctl/sysctl*
                         'sysctl (with-meta @#'sysctl/sysctl {:sci/macro true})}
   'spire.module.upload {'upload* upload/upload*
                         'upload (with-meta @#'upload/upload {:sci/macro true})}
   'spire.module.user {'user* user/user*
                       'user (with-meta @#'user/user {:sci/macro true})}


   })

(def classes
  {'java.lang.System System
   'java.time.Clock java.time.Clock
   'java.time.DateTimeException java.time.DateTimeException
   'java.time.DayOfWeek java.time.DayOfWeek
   'java.time.Duration java.time.Duration
   'java.time.Instant java.time.Instant
   'java.time.LocalDate java.time.LocalDate
   'java.time.LocalDateTime java.time.LocalDateTime
   'java.time.LocalTime java.time.LocalTime
   'java.time.Month java.time.Month
   'java.time.MonthDay java.time.MonthDay
   'java.time.OffsetDateTime java.time.OffsetDateTime
   'java.time.OffsetTime java.time.OffsetTime
   'java.time.Period java.time.Period
   'java.time.Year java.time.Year
   'java.time.YearMonth java.time.YearMonth
   'java.time.ZonedDateTime java.time.ZonedDateTime
   'java.time.ZoneId java.time.ZoneId
   'java.time.ZoneOffset java.time.ZoneOffset
   'java.time.temporal.TemporalAccessor java.time.temporal.TemporalAccessor
   'java.time.format.DateTimeFormatter java.time.format.DateTimeFormatter
   'java.time.format.DateTimeFormatterBuilder java.time.format.DateTimeFormatterBuilder
   }
  )
