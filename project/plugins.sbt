// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository 
resolvers ++= Seq("typesafe" at "http://repo.typesafe.com/typesafe/releases/",
  Resolver.file("file",  new File(Path.userHome.absolutePath+"/.ivy2/local/")),
  "Scalaz Bintray Repo"  at "http://dl.bintray.com/scalaz/releases"
)

// The Sonatype snapshots repository
resolvers += "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"


// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.7")

addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.0.0")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")


