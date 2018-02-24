import sbt._
import Keys._

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
}
