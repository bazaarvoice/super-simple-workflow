lazy val core = (project in file("sswf-core"))
   .configs(Configs.all: _*)
   .settings(Commons.settings: _*)
   .settings(Testing.settings: _*)
   .settings(
     name := "sswf",
     libraryDependencies ++= Seq(
       "com.amazonaws" % "aws-java-sdk" % "1.11.73",
       "org.joda" % "joda-convert" % "1.2"
     ),
     libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "e2e,it,test"
   )
   .settings(Commons.publish: _*)

lazy val guava20 = (project in file("sswf-guava-20"))
   .settings(Commons.settings: _*)
   .settings(
     name := "sswf-guava-20",
     libraryDependencies ++= Seq(
       "com.google.guava" % "guava" % "20.0",
       "org.slf4j" % "slf4j-api" % "1.7.22",
       "org.slf4j" % "slf4j-simple" % "1.7.22",
       "com.google.code.findbugs" % "jsr305" % "2.0.3" // work around a bug in scalac https://issues.scala-lang.org/browse/SI-8978
     )
   )
   .settings(Commons.publish: _*)
   .dependsOn(core)

lazy val example = (project in file("sswf-java-example"))
   .settings(Commons.settings: _*)
   .settings(
     name := "sswf-example",
     mainClass in(Compile, run) := Some("example.ExampleWorkflowService"),
     fork in run := true
   )
   .settings(Commons.nopublish: _*)
   .dependsOn(guava20)

lazy val root = (project in file("."))
   .settings(Commons.settings: _*)
   .settings(
     name := "sswf-root"
   )
   .settings(Commons.nopublish: _*)
   .aggregate(core, guava20, example)
