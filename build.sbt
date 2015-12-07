name := "sswf"

organization := "com.bazaarvoice"

version := "0.6-SNAPSHOT"

scalaVersion := "2.11.7"

crossScalaVersions := Seq("2.10.4", "2.11.7")

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings")

credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

resolvers ++= Seq(
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies ++= Seq(
  // aws
  "com.amazonaws" % "aws-java-sdk" % "1.10.8",
  // bring in optional dep (that breaks the build)
  "org.joda" % "joda-convert" % "1.2"
)

net.virtualvoid.sbt.graph.Plugin.graphSettings

mainClass in(Compile, run) := Some("example.ExampleWorkflowService")

// Generate pom.xml so maven modules can depend on me.
publishMavenStyle := true

// sonatype oss publishing:
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
   <url>http://github.com/bazaarvoice/super-simple-workflow</url>
      <licenses>
        <license>
          <name>The Apache Software License, Version 2.0</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>http://github.com/bazaarvoice/super-simple-workflow</url>
        <connection>scm:git:git@github.com:bazaarvoice/super-simple-workflow.git</connection>
        <developerConnection>scm:git:git@github.com:bazaarvoice/super-simple-workflow.git</developerConnection>
        <tag>HEAD</tag>
      </scm>
      <developers>
        <developer>
          <id>vvcephei</id>
          <name>John Roesler  </name>
          <url>https://github.com/vvcephei/</url>
        </developer>
      </developers>
   )
