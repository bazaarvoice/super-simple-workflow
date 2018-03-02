import sbt._
import Keys._
import com.typesafe.sbt.pgp.PgpKeys._

object Commons {
  val settings: Seq[Def.Setting[_]] = Seq(
    version := "6.4",
    organization := "com.bazaarvoice",
    scalaVersion := "2.12.1",
    crossScalaVersions := Seq("2.12.1"),
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings"),
    javacOptions in (Compile, compile) ++= Seq("-source", "1.8", "-target", "1.8"),

    resolvers ++= Seq(
      "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"
    )
  )
  val nopublish: Seq[Def.Setting[_]] = Seq(
    publishLocal := {},
    publishSigned := {},
    publishLocalSigned := {}
  )
  val publish: Seq[Def.Setting[_]] = Seq(
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
}
