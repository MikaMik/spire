# Discussion

## Execution Contexts

Spire can execute modules locally or on a remote machine. Each type is
called an execution context. The default execution context is to
execute locally. You can change this default context to be a remote
context. This is most useful for work involving the REPL.

Contexts are generally invoked by the `local`, `ssh` or `ssh-group`
macro. The forms in the body of the macro are evaluated in that
context, and at the end of the macro the old context is returned. Thus
contexts are lexically scoped.

### Local Context

Local context is the default context if no other context is invoked.

Local context can be set by wrapping code in the `local` macro. eg:

```clojure
(local
    ...these modules run locally...
    )
```

### Remote Contexts

Spire connects to remote machines with the `ssh` or `ssh-group`
commands. Each takes one or more _connection descriptions_ that define
how to connect. There are two types of connection descriptions: A
compact _host string_ format, or a more powerful hashmap format.

#### Connection Descriptions

##### Host Strings

A host string is of the format `username@hostname:port`. The
`username` and `port` sections are optional. If no username is
specified, the present user's username is used. If no port is
specified then a default port of 22 is used.

##### Connection Hashmaps

A connection hashmap can be used instead of a host string. The hashmap
can contain some subset of the following keys:

* :host-string
* :username
* :hostname
* :port
* :password
* :identity
* :passphrase
* :private-key
* :public-key
* :agent-forwarding
* :strict-host-key-checking
* :accept-host-key
* :key

###### host-string

This is a convenience setting for when you wish to add extra options
to an existing host string config. It allows you to specify some
subset of `:username`, `:hostname` and `:port` in a single setting.

###### username

The username to connect as.

###### hostname

A machine hostname or an IP number to connect to.

###### port

The TCP port to use to initiate the ssh connection.

###### password

Authenticate with the remote server using a plain text password. The
password to use is specified as the value for this key. If password
authentication is in use and this setting is not provided, `spire`
will prompt for a password to be entered at the terminal.

###### identity

Authenticate with the remote server using a private ssh key identity
stored in a file. The value should be the path to the private key
file.

###### passphrase

If the identity specified is encrypted, decrypt it with this
passphrase. If it is encrypted and no passphrse is given `spire` will
ask for a passphrase on the terminal.

###### private-key

Authenticate with the remote server using a private ssh key
identity. The identity to use is specified as the value for this key.

Note: This value is *not* a filename but the contents of the identity
file itself.

###### public-key

Presently, spire does not use the public key field, but it is passed
to the underlying JSch ssh implementation for it's use. You may need
to supply this if you are extending spire itself with new JSch
functionality.

###### agent-forwarding

Set this value to `true` to enable SSH authentication agent forwarding
on the connection. This requires a local SSH agent to be
running. Default value is `false`.

###### strict-host-key-checking

Setting this value as `false` will allow a connection to establish to
any remote host without checking its host key for validity. Default
value when unspecified is `true`, ie. Check the remote host's host
key.

###### accept-host-key

This value controls the automatic acceptance of an unknown remote host
key. If set to `true`, any host key will be accepted and added to the
`known_hosts` file. If set to a string, `spire` will compare the
remote host key's fingerprint with that specified in the string. If
they match, the key will be added to the known_hosts file and the
connection will be established.

###### key

Specify a custom key to key the return value in group connections. The
default is to key the return values by the host-string.

#### ssh

The `ssh` macro initiates a connection to a single remote host via ssh
and then executes the body of the form in an implicit do block. It
takes the form:

```clojure
(ssh connection-config
    ...body...)
```

`connection-config` can be a host-string or a hashmap defining the
connection.

Once connected, each form in `body` will be executed in turn.

##### Return Value

The result of the evaluation of the final form in body is returned by
`ssh` unaltered.

#### ssh-group

The `ssh-group` macro initiates ssh connections to more than one
remote host. Once the connections are established it then spawns a
thread for each connection and executes the body of the form in each
thread. It takes the form:

```clojure
(ssh-group [connection-conf-1 connection-conf-2 ... connection-conf-n]
    ...body...)
```

Each `connection-config` can be a host-string or a hashmap defining
the connection.

If one thread/connection experiences a failure, its execution will
stop, but the others will continue.

##### Return Value

`ssh-group` will take the return value from the last form evaluated by
each thread and collate them together into a hashmap. The values for
each connection will be stored under a key. This key will be the
host-string by default, but you can override this return value key by
specifying a custome `:key` in the `connection-conf` hashmap passed in
to `ssh-group`.

#### Reconnection Issues

Both `ssh` and `ssh-group`, when used in isolation, will open a
connection when the call is entered, and close a connection when the
body exits. Consider the following:

```clojure
(ssh "host-1" body-1)
(ssh "host-2" body-2)
(ssh "host-1" body-3)
```

In the above case, spire will open a connection to `host-1`, then run
`body-1`, and then close the connection to `host-1`. It will then open
a connection to `host-2`, run `body-2`, and then close the connection
to `host-2`. It wil then *reopen* the connection to `host-1`, run
`body-3` and then close the connection to `host-1`.

Thus the connection to `host-1` is performed *twice*, including all
the connection negotiation and authentication. This approach is
perfectly valid, but with a larger and more complex script, this
connection overhead may become a substantial performance
bottleneck.

There are two ways to mitigate this issue. Nested connections and
pre-connecting.

#### Nested Connections

To mitigate this issue you can nest connections.

If `ssh` or `ssh-group` is called, and a connection to the host is
already established, spire will use that existing connection but use a
new *channel* on it to run the body.

Thus the previous example could be rewritten:

```clojure
(ssh "host-1" body-1
    (ssh "host-2" body-2)
    body-3)
```

In this case, each host is only connected to once.

You can also nest more deely, if you wish. This is useful if there was
some result of an operation on `host-1` that you are going to use on
`host-2` thus:

```clojure
(ssh "host-1"
    (ssh "host-2"
        (use-something-from (ssh "host-1" (get-file ...)))
        ...more...
        )
    )
```

**Warning** When nesting an ssh connection context inside `ssh-group`,
the inner body code will be run multiple times, one for each
`ssh-group` thread. In many such cases it will be prudent to gather
that information outside of the `ssh-group` call and pass the data
through.

```clojure
(ssh "host-1"
    (let [data (get-file ...)]
        (ssh-group ["host-2" "host-3"]
            (do-something-wth data))))
```

#### Pre-connection

Another way to mitigate excessive reconnections is to pre-connect to
your machines using the functions
`spire.default/push-ssh!`. Additionally `spire.default/empty!` can
close all the connections.

```clojure
(push-ssh! "host-1") ;; connects to host-1
(push-ssh! "host-2") ;; connects to host-2
(ssh "host-1" ... ) ;; reuses host-1 connection
(ssh "host-2" ... ) ;; reuses host-2 connection
(empty!) ;; disconnects from both host-1 and host-2. Or just let the script exit
```

#### Agent Forwarding

To activate ssh authentication agent forwarding on a connection, set
`:auth-forward` to `true` in the host config:

```clojure
(ssh {:username "root"
      :hostname "remote-host"
      :agent-forwarding true}
  (shell {:cmd "ssh -T git@github.com"}))
```

Note: ssh agent forwarding requires running a ssh-agent on your local
computer to work.

## Escalating priviledges

### Sudo

The user used to execute module commands can be changed with `sudo` or
`sudo-user`.

#### sudo-user macro

`sudo-user` takes a configuration hashmap whose keys and values
configure the execution of the sudo command. The body can be one or
more forms.

```clojure
(sudo-user config body...)
```

The config form is a hashmap with a subset of the following keys:

 - :username
 - :uid
 - :group
 - :gid
 - :password

For example:

```clojure
(ssh "user@host"
  (sudo-user {:username "root"
              :password "my-sudo-password"}
    (do-something ... )))
```

#### sudo macro

`sudo` executes the body of the macro using the plain sudo command on
the remote host.

```clojure
(sudo body...)
```

#### sudo password caching

When using a password based sudo you only need to specify the password
once. Spire will cache the password it has used for a connection, and
if prompted for a password again and a new one is not supplied, the
last used one will be tried. In this way you do not have to keep
supplying the same password over and over. For example:

```clojure
(ssh "user@host"
   (sudo-user {:password "my-sudo-password"}
      (do-stuff-as-root))
   (do-stuff-as-user)
   (sudo
      (do-more-stuff-as-root)))
```

## nREPL Connections

Spire can be started with the `--nrepl-server` flag to launch an nREPL
service. For example:

```shell-session
$ spire --nrepl-server 6543
Started nREPL server at 127.0.0.1:6543
```

Now in an editor that supports a clojure nREPL, connect to this address.

### Setting the default context for the nREPL

By default, if the code executed is not in the body of a context macro
such as `local`, `ssh` or `ssh-group`, then the code is executed in a
local context. This can be changed by the functions in the
`spire.default` namespace.

Note: The macros `local`, `ssh` and `ssh-group` _always_ override any
default context setting.

When there is no execution context macro body in play, spire falls
back to the default context. This context is the most recent value on
a _default context stack_ that you can change with the following functions.

#### spire.default/set-ssh!

Sets the present default connection context to an ssh connection with
the chosen settings.

```
user> (set-ssh! "epiccastle.io")
true
user> (shell {:cmd "hostname"})
{:exit 0, :out "epiccastle\n", :err "", :out-lines ["epiccastle"], :result :ok}
```

#### spire.default/set-local!

Sets the present default connection context to an ssh connection with
the chosen settings.

```
user> (set-local!)
true
user> (shell {:cmd "hostname"})
{:exit 0, :out "vash\n", :err "", :out-lines ["vash"], :result :ok}
```

#### spire.default/push-ssh!

This pushes a new ssh connection context onto the default context stack.

example (1):

```
user> (push-ssh! "epiccastle.io")
true
user> (shell {:cmd "hostname"})
{:exit 0, :out "epiccastle\n", :err "", :out-lines ["epiccastle"], :result :ok}
```

#### spire.default/push-local!

This pushes a new local connection context onto the default context stack.

example (2):

```
user> (push-local!)
true
user> (shell {:cmd "hostname"})
{:exit 0, :out "vash\n", :err "", :out-lines ["vash"], :result :ok}
```

#### spire.default/pop!

This pops the top connection context off the stack and returns the
present default connection context to its previous setting.

For example, after doing (1) and (2) above,

then example (3):

```
user> (pop!)
true
user> (shell {:cmd "hostname"})
{:exit 0, :out "epiccastle\n", :err "", :out-lines ["epiccastle"], :result :ok}
```

#### spire.default/empty!

This pops all the connection contexts off and clears the stack. It
returns the default connection context to a local one.

After doing (1), (2) and (3) above:

```
user> (empty!)
nil
user> (shell {:cmd "hostname"})
{:exit 0, :out "vash\n", :err "", :out-lines ["vash"], :result :ok}
```

## Requiring External Code

Code defined in external files can be referenced in your mainline
by using clojures standard `require` semantics. This can be done as
a plain require. For example:

```clojure
(require '[roles.nginx :as nginx])
```

Or in a namespace declaration such as:

```clojure
(ns infra
  (:require [roles.nginx :as nginx]))
```

The file to be included `nginx.clj` whereever it is found should begin
with a matching `ns` declaration:

```clojure
(ns roles.nginx)
...
```

### External Code Search Path

Required code will be loaded from a standard namespace directory
structure. For example, `roles.nginx` will be loaded from a file
`roles/nginx.clj`.

This path will be used relative to the containing folder of the
executing script, if spire is invoked with a code file path, or the
present directory, if spire is invoked with `-e` to evaluate a string.

So, for example: If spire was invoked `spire path/to/script.clj` then
the above required file would be loaded from
`path/to/roles/nginx.clj`. Alternatively if spire was invoked `spire
-e "(require '[roles.nginx])"` then the file would be loaded from
`roles/nginx.clj`

#### Altering the Search Path

More complex library layouts can be facilitated via the environment
variable `SPIREPATH`. If this environment variable is set, it should
contain a colon `:` seperated list of paths to search for the library
code. For example in the following case:

```shell
SPIREPATH=a/b:c/d/e:../f:. spire -e "(require '[role.nginx])"
```

The file `nginx.clj` would be looked for in the following order

```
a/b/role/nginx.clj
c/d/e/role/nginx.clj
../f/role/nginx.clj
./role/nginx.clj
```

If spire path is set and the present directory is not included, then
it will not be searched. In this way settings `SPIREPATH` overrides
the default search location behaviour completely.

#### Spire modules and their layout

By default the complete set of available modules are present to be
used without a namespace qualifier in the scripts default
namespace. So for example `spire -e '(ssh "localhost" (get-fact))'`
works without any namespaces specified for `ssh` or `get-fact`.

These names are actually interned in the `user` namespace, and the
default namespace for code evaluation is `user`. Thus, if you specify
another namespace for execution via a `ns` declaration you will
discover that you will need to require each module function from each
namespace. For example:

```clojure
(ns infra
  (:require [spire.module.apt :as apt]
            [spire.module.download :as download]))

...

(apt/apt ...)
(download/download ...)
```

This can become very tiresome so a namespace `spire.modules` is
provided that contains every default module function in a single
namespace. Thus you can restore the default root script behavoir in
any namespace as follows:

```clojure
(ns infra
  (:require [spire.modules :refer :all]))
```

### Loading External Code

An alternative method to bringing in external code is with
`load-file`.  This loads the code from an external clojure file and
evaluates it in the present namespace context. For example:

```shell
$ cat test.clj
(* 10 n)
$ spire -e '(def n 5) (load-file "test.clj")'
50
```

## Output

Output printing is controlled by the `--output` flag. You can specify a
snippet of edn as a value. (This value will be used as the dispatch
value `driver` when calling the output functions).

### :default

The default output driver is selected with `--output :default`. This driver
tries to collate the output of the state together in a minimal way. It
uses colour. It prints errors inline. It prints `upload` and
`download` copy progress bars.

### :quiet

The quiet output driver is selected with `--output :quiet`. This driver
prints nothing.

### :events

The events output driver is selected with `--output :events`. This prints a
coloured, pretty printed vector for every called output function. The
format of the vector printed is `[type filename form meta host-config
& arguments]`

## Threading Model

## Facts

## Environment

## Command Line Arguments
