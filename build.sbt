


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
     libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "e2e,it,test",
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
   .dependsOn(core)

lazy val example = (project in file("sswf-java-example"))
   .settings(Commons.settings: _*)
   .settings(
     name := "sswf-example",
     mainClass in(Compile, run) := Some("example.ExampleWorkflowService")
   )
   .dependsOn(guava20)
