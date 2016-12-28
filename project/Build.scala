import sbt._

object SSWFBuild extends Build {
  lazy val root = Project(id = "root", base = file("."))
     .configs(Configs.all: _*)
     .settings(Testing.settings: _*)
}

