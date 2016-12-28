import Dependencies._


lazy val core = project
  .settings(Commons.settings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk" % "1.11.73",
      "org.joda" % "joda-convert" % "1.2"
    ),
    libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "test",
    publishMavenStyle := true,
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
	Some("snapshots" at nexus + "content/repositories/snapshots")
      else
	Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    pomExtra := <url>http://github.com/bazaarvoice/super-simple-workflow</url>
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
	   <name>John Roesler</name>
	   <url>https://github.com/vvcephei/</url>
	 </developer>
       </developers>
  )

lazy val example = project
  .settings(Commons.settings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk" % "1.11.73",
      "org.joda" % "joda-convert" % "1.2"
    ),
    mainClass in(Compile, run) := Some("example.ExampleWorkflowService")
  )
  .dependsOn(core)
