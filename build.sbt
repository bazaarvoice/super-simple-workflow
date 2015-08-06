name := "sswf"

organization := "com.bazaarvoice"

version := "0.0"

scalaVersion := "2.11.7"

credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

resolvers ++= Seq(
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies ++= Seq(
    // aws
    "com.amazonaws" % "aws-java-sdk" % "1.10.8"
)

//libraryDependencies ++= Seq(
    //"org.scalatest" %% "scalatest10" % "2.0" % "test",
    //"org.mockito" % "mockito-core" % "1.9.0" % "test"
//)

net.virtualvoid.sbt.graph.Plugin.graphSettings

// Generate pom.xml so maven modules can depend on me.
publishMavenStyle := true

mainClass in (Compile, run) := Some("example.ExampleWorkflowService")
