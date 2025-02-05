import Dependencies._

val mainPluginClass = "org.winlogon.whisperchat.WhisperChatPlugin"
ThisBuild / scalaVersion     := "3.3.4"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "org.winlogon"
ThisBuild / organizationName := "winlogon"
Compile / mainClass := Some(mainPluginClass)

// GitHub CI
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("21"))
ThisBuild / publishTo := None
publish / skip := true

crossScalaVersions := Seq("3.3.4")

lazy val root = (project in file("."))
  .settings(
    name := "whisperchat",
    libraryDependencies += munit % Test
  )

// Merge strategy for avoiding conflicts in dependencies
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}

assembly / mainClass := Some(mainPluginClass)

libraryDependencies ++= Seq(
  "io.papermc.paper" % "paper-api" % "1.21.4-R0.1-SNAPSHOT" % Provided,
)

resolvers += "papermc-repo" at "https://repo.papermc.io/repository/maven-public/"
