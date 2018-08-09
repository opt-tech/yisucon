name := "isuwitter"
 
version := "1.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.2"

libraryDependencies ++= Seq( jdbc , ehcache , ws , specs2 % Test , guice )
