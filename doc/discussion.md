# Discussion

## Connections

Spire connects to remote machines with the `ssh` or `ssh-group` commands. Each takes one or more _connection descriptions_ that define how to connect. There are two types of connection descriptions: A compact _host string_ format, or a more powerful hashmap format.

### Connection Descriptions

#### Host Strings

A host string is of the format `username@hostname:port`. The `username` and `port` sections are optional. If no username is specified, the present user's username is used. If no port is specified then a default port of 22 is used.

#### Connection Hashmaps

A connection hashmap can be used instead of a host string. The hashmap can contain some subset of the following keys:

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

##### host-string

This is a convenience setting for when you wish to add extra options to an existing host string config. It allows you to specify some subset of `:username`, `:hostname` and `:port` in a single setting.

##### username

The username to connect as.

##### hostname

A machine hostname or an IP number to connect to.

##### port

The TCP port to use to initiate the ssh connection.

##### password

Authenticate with the remote server using a plain text password. The password to use is specified as the value for this key. If password authentication is in use and this setting is not provided, `spire` will prompt for a password to be entered at the terminal.

##### identity

Authenticate with the remote server using a private ssh key identity stored in a file. The value should be the path to the private key file.

##### passphrase

If the identity specified is encrypted, decrypt it with this passphrase. If it is encrypted and no passphrse is given `spire` will ask for a passphrase on the terminal.

##### private-key

Authenticate with the remote server using a private ssh key identity. The identity to use is specified as the value for this key.

Note: This value is *not* a filename but the contents of the identity file itself.

##### public-key

Presently, spire does not use the public key field, but it is passed to the underlying JSch ssh implementation for it's use. You may need to supply this if you are extending spire itself with new JSch functionality.

##### agent-forwarding

Set this value to `true` to enable SSH authentication agent forwarding on the connection. This requires a local SSH agent to be running. Default value is `false`.

##### strict-host-key-checking

Setting this value as `false` will allow a connection to establish to any remote host without checking its host key for validity. Default value when unspecified is `true`, ie. Check the remote host's host key.

##### accept-host-key

This value controls the automatic acceptance of an unknown remote host key. If set to `true`, any host key will be accepted and added to the `known_hosts` file. If set to a string, `spire` will compare the remote host key's fingerprint with that specified in the string. If they match, the key will be added to the known_hosts file and the connection will be established.

##### key

Specify a custom key to key the return value in group connections. The default is to key the return values by the host-string.

### ssh

The `ssh` macro initiates a connection to a single remote host via ssh and then executes the body of the form in an implicit do block. It takes the form:

```clojure
(ssh connection-config
    ...body...)
```

`connection-config` can be a host-string or a hashmap defining the connection.

Once connected, each form in `body` will be executed in turn.

#### Return Value

The result of the evaluation of the final form in body is returned by `ssh` unaltered.

### ssh-group

The `ssh-group` macro initiates ssh connections to more than one remote host. Once the connections are established it then spawns a thread for each connection and executes the body of the form in each thread. It takes the form:

```clojure
(ssh-group [connection-conf-1 connection-conf-2 ... connection-conf-n]
    ...body...)
```

Each `connection-config` can be a host-string or a hashmap defining the connection.

If one thread/connection experiences a failure, its execution will stop, but the others will continue.

#### Return Value

`ssh-group` will take the return value from the last form evaluated by each thread and collate them together into a hashmap. The values for each connection will be stored under a key. This key will be the host-string by default, but you can override this return value key by specifying a custome `:key` in the `connection-conf` hashmap passed in to `ssh-group`.

### Nested Connections

Both `ssh` and `ssh-group`, when used in isolation, will open a connection when the call is entered, and close a connection when the body exits. Consider the following:

```clojure
(ssh "host-1" body-1)
(ssh "host-2" body-2)
(ssh "host-1" body-3)
```

In the above case, spire will open a connection to `host-1`, then run `body-1`, and then close the connection to `host-1`. It will then open a connection to `host-2`, run `body-2`, and then close the connection to `host-2`. It wil then *reopen* the connection to `host-1`, run `body-3` and then close the connection to `host-1`.

Thus the connection to `host-1` is performed *twice*, including all the connection negotiation and authentication. This approach is perfectly valid, but with a larger and more complex script, this connection overhead may become a substantial performance bottleneck. To mitigate this issue you can nest connections.

If `ssh` or `ssh-group` is called, and a connection to the host is already established, spire will use that existing connection but use a new *channel* on it to run the body.

Thus the previous example could be rewritten:

```clojure
(ssh "host-1" body-1
    (ssh "host-2" body-2)
    body-3)
```

In this case, each host is only connected to once.

You can also nest more deely, if you wish. This is useful if there was some result of an operation on `host-1` that you are going to use on `host-2` thus:

```clojure
(ssh "host-1"
    (ssh "host-2"
        (use-something-from (ssh "host-1" (get-file ...)))
        ...more...
        )
    )
```

**Warning** When nesting an ssh connection context inside `ssh-group`, the inner body code will be run multiple times, one for each `ssh-group` thread. In many such cases it will be prudent to gather that information outside of the `ssh-group` call and pass the data through.

```clojure
(ssh "host-1"
    (let [data (get-file ...)]
        (ssh-group ["host-2" "host-3"]
            (do-something-wth data))))
```

### Agent Forwarding

## Escalating priviledges

### Sudo

The user used to execute module commands can be changed with `sudo` or `sudo-user`.

#### sudo-user macro

`sudo-user` takes a configuration hashmap whose keys and values configure the execution of the sudo command. The body can be one or more forms.

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

`sudo` executes the body of the macro using the plain sudo command on the remote host.

```clojure
(sudo body...)
```

## Output

## Threading Model

## Facts

## Environment

## Command Line Arguments
