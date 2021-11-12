lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(
    name := """image-scavenger""",
    version := "2.8.x",
    scalaVersion := "2.13.6",
    libraryDependencies ++= Seq(
      guice,
      evolutions,
      "com.typesafe.play" %% "play-slick" % "5.0.0",
      "com.typesafe.play" %% "play-slick-evolutions" % "5.0.0",
      "com.h2database" % "h2" % "1.4.199",
      "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test,
    ),
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-Xfatal-warnings"
    )
  )
