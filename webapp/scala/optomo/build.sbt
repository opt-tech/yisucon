name := "optomo"
 
version := "1.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.6"

libraryDependencies ++= Seq( ehcache , ws , specs2 % Test , guice )
libraryDependencies += "org.scalikejdbc" %% "scalikejdbc" % "3.2.4"
libraryDependencies += "org.scalikejdbc" %% "scalikejdbc-config" % "3.2.1"
libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.40"
