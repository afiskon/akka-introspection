name := "akka-introspection"

version := "0.1"

scalaVersion := "2.11.7"

scalacOptions ++= Seq("-Xmax-classfile-name", "100")

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.3.12"
  )
