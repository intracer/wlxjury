https://commons.wikimedia.org/wiki/Commons:WLX_Jury_Tool 

[![Build Status](https://travis-ci.org/intracer/wlxjury.svg?branch=master)](https://travis-ci.org/intracer/wlxjury?branch=master)
[![codecov.io](http://codecov.io/github/intracer/wlxjury/coverage.svg?branch=master)](http://codecov.io/github/intracer/wlxjury?branch=master)

## Running with Vagrant

Create and provision the vagrant box with
`vagrant up`

Login to vagrant box with

`vagrant ssh`

cd to directory with project source code

`cd /vagrant`

run the application with sbt

`sbt run`

the terminal will have the following output

```
vagrant@vagrant-ubuntu-trusty-64:/vagrant$ sbt run
[info] Loading project definition from /vagrant/project
[info] Set current project to wlxjury (in build file:/vagrant/)

--- (Running the application, auto-reloading is enabled) ---

[info] p.c.s.NettyServer - Listening for HTTP on /0:0:0:0:0:0:0:0:9000

(Server started, use Ctrl+D to stop and go back to the console...)
```

Now open [http://localhost:9000](http://localhost:9000)

### TODO creating root admin

## Development

### Auto-reloading
Interpreted languages like Python or PHP always run the latest source code that was modified by developer. That is not the case for compiled language like Java or Scala. However Play Framework supports auto-reloading if the source code changes and you try to open some web-page again. On new http request if source code is modified, changed files will be recompiled and the application will be reloaded.

### sbt Triggered compilation
The drawback of autoreloading is that recompilation will only happen on new page request. Using sbt [Triggered compilation](http://www.scala-sbt.org/0.13/docs/Howto-Triggered.html) sbt can automatically recompile in the background the moment you save updated source code. To do that run sbt without commands
`sbt`
This will open the project in sbt console. Enter the command
`~ run`
Application will start and will compile the changes in the backgroud.

