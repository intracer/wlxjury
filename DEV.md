## React UI (Contests)

### Dev workflow

The UI lives in `ui/` as a standalone Vite project. It proxies `/api/*` to Play on port 9000, so both must be running.

**Terminal 1 — Play backend:**
```bash
WLXJURY_DB_HOST=... WLXJURY_DB=... WLXJURY_DB_USER=... WLXJURY_DB_PASSWORD=... sbt run
```

**Terminal 2 — Vite dev server:**
```bash
cd ui
npm install   # first time only
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). The UI hot-reloads on save. API calls go to Play at port 9000 via the Vite proxy.

### Tests

```bash
cd ui && npm test          # run once (CI mode)
cd ui && npm run test:watch  # watch mode for development
```

Tests are also run as part of `sbt test` via the `uiTest` task.

### Deploying (production build)

1. Build the UI:
   ```bash
   cd ui && npm run build
   ```
   This outputs static assets to `ui/dist/`.

2. Copy to the Play public directory before running `sbt dist`:
   ```bash
   cp -r ui/dist/* public/ui/
   ```

3. Package and deploy as normal:
   ```bash
   sbt dist
   ```

Play serves the built UI at `/ui` via the routes added to `conf/routes`. The API at `/api/*` continues to be served by Tapir/Pekko HTTP on the same port.

---

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
The drawback of autoreloading is that recompilation will only happen on new page request. Using [Triggered compilation](http://www.scala-sbt.org/0.13/docs/Howto-Triggered.html) sbt can automatically recompile in the background the moment you save updated source code. To do that run sbt with command `~run`

`sbt ~run`

Application will start and will compile the changes in the background.

You can read more on sbt commands in [sbt](http://www.scala-sbt.org/0.13/docs/Running.html) and [Play](https://www.playframework.com/documentation/2.4.x/PlayConsole) documentations. Note that Play documentation uses the command `activator` for Lightbend Activator that comes with full Play distribution or can be downloaded separately, but Activator is build on sbt and you can should run `sbt` instead of `activator` (unless you decide to install Activator too)

### IDEs
One of the best IDEs for Scala is [Intellij IDEA](https://en.wikipedia.org/wiki/IntelliJ_IDEA). It has free and open-source Community edition and Scala plugin. Paid (or [available for free for open-source project communities like Wikimedia](https://lists.wikimedia.org/pipermail/wikitech-l/2016-May/085558.html)) Ultimate edition also provides additional support for Play Framework features like [template](https://www.playframework.com/documentation/2.4.x/ScalaTemplates) and [route](https://www.playframework.com/documentation/2.4.x/ScalaRouting) files.

There are Scala plugins for [Eclipse](http://scala-ide.org/) and [NetBeans](https://en.wikipedia.org/wiki/NetBeans) IDEs. 

There is also ENSIME project that "brings Scala and Java IDE-like features to your favourite text editor". See the [supported features in different text editors](http://ensime.github.io/editors/) like Emacs, Vim or Sublime.

Read more in [Play](https://www.playframework.com/documentation/2.4.x/IDE) and [IntelliJ IDEA](https://www.jetbrains.com/help/idea/2016.2/getting-started-with-play-2-x.html) documentation. Note that JetBrains IDE documentation on Play support assumes Ultimate edition, but parts of it related to Scala and Sbt features apply also for Community edition.

