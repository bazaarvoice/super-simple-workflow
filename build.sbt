lazy val core = (project in file("sswf-core"))
   .configs(Configs.all: _*)
   .settings(Commons.settings: _*)
   .settings(Testing.settings: _*)
   .settings(Commons.publish: _*)
   .settings(
     name := "sswf",
     libraryDependencies ++= Seq(
       "com.amazonaws" % "aws-java-sdk" % "1.10.69",
       "org.joda" % "joda-convert" % "1.2"
     ),
     libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "e2e,it,test"
   )

lazy val example = (project in file("sswf-java-example"))
   .settings(Commons.settings: _*)
   .settings(Commons.publish: _*)
   .settings(
     name := "sswf-example",
     libraryDependencies ++= Seq(
       "com.amazonaws" % "aws-java-sdk" % "1.10.69",
       "org.joda" % "joda-convert" % "1.2"
     ),
     sources in (Compile, doc) := Seq(),
     mainClass in(Compile, run) := Some("example.ExampleWorkflowService")
   )
   .dependsOn("core")

lazy val root = (project in file("."))
   .settings(Commons.settings: _*)
   .settings(
     name := "sswf-root"
   )
   .settings(Commons.nopublish: _*)
   .aggregate(core, example)
