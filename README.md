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

### IDEs
One of the best IDEs for Scala is [Intellij IDEA](https://en.wikipedia.org/wiki/IntelliJ_IDEA). It has free and open-source Community version and Scala plugin. Paid (or [available for free for open-source project communities like Wikimedia](https://lists.wikimedia.org/pipermail/wikitech-l/2016-May/085558.html)) Ultimate version also provides additional support for Play Framework like [template](https://www.playframework.com/documentation/2.4.x/ScalaTemplates) and [route](https://www.playframework.com/documentation/2.4.x/ScalaRouting) files.

There are Scala plugins for [Eclipse](http://scala-ide.org/) and [NetBeans](https://en.wikipedia.org/wiki/NetBeans) IDEs. 

There is also ENSIME project that "brings Scala and Java IDE-like features to your favourite text editor". See the [supported features in different text editors](http://ensime.github.io/editors/) like Emacs, Vim or Sublime.

