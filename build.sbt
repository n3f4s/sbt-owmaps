lazy val root = (project in file("."))
  .settings(
    name := "sbt-owmaps",
    version := "1.0",
    organization := "com.github.n3f4s",
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-jackson" % "3.+",
    ),
    sbtPlugin := true,
  )
