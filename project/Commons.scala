import sbt._
import Keys._
import com.typesafe.sbt.pgp.PgpKeys.{publishLocalSigned, publishSigned}

object Commons {
  val settings: Seq[Def.Setting[_]] = Seq(
    version := "5.0.1",
    organization := "com.bazaarvoice",
    scalaVersion := "2.11.7",
    crossScalaVersions := Seq("2.10.4", "2.11.7"),
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
    // POM settings for Sonatype
    homepage := Some(url("http://github.com/bazaarvoice/super-simple-workflow")),
    scmInfo := Some(ScmInfo(url("http://github.com/bazaarvoice/super-simple-workflow"), "git@github.com:vvcephei/super-simple-workflow.git")),
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(id="vvcephei", name="John Roesler", email="john@vvcephei.org", url=url("https://github.com/vvcephei/"))
    ),
    publishMavenStyle := true,
    // Add sonatype repository settings
    publishTo := Some(
      if (isSnapshot.value)
        Opts.resolver.sonatypeSnapshots
      else
        Opts.resolver.sonatypeStaging
    ),
//    publishMavenStyle := true,
//    publishTo := {
//      val nexus = "https://oss.sonatype.org/"
//      if (isSnapshot.value)
//        Some("snapshots" at nexus + "content/repositories/snapshots")
//      else
//        Some("releases" at nexus + "service/local/staging/deploy/maven2")
//    },
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false }
//    pomExtra := <url>http://github.com/bazaarvoice/super-simple-workflow</url>
//       <licenses>
//         <license>
//           <name>The Apache Software License, Version 2.0</name>
//           <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
//           <distribution>repo</distribution>
//         </license>
//       </licenses>
//       <scm>
//         <url>http://github.com/bazaarvoice/super-simple-workflow</url>
//         <connection>scm:git:git@github.com:bazaarvoice/super-simple-workflow.git</connection>
//         <developerConnection>scm:git:git@github.com:bazaarvoice/super-simple-workflow.git</developerConnection>
//         <tag>HEAD</tag>
//       </scm>
//       <developers>
//         <developer>
//           <id>vvcephei</id>
//           <name>John Roesler</name>
//           <url>https://github.com/vvcephei/</url>
//         </developer>
//       </developers>
  )
}
