import sbt.Keys._

val libVersion = "1.0"

val scala = "2.12.4"

val commonDependencies = Seq(
  "org.slf4j" % "slf4j-api" % "1.7.25",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.lihaoyi" %% "sourcecode" % "0.1.3",
  "org.scalatest" %% "scalatest" % "3.0.4" % Test,
  "org.scalacheck" %% "scalacheck" % "1.13.4" % Test
)

def commonSettings(_name: String) = Seq(
  scalaVersion := scala,
  version := libVersion,
  libraryDependencies ++= commonDependencies,
  name := _name,
)

def grpcProtocolSettings = {
  import com.trueaccord.scalapb.compiler.Version

  Seq(
    PB.targets in Compile := Seq(
      scalapb.gen(singleLineToString = true) -> (sourceManaged in Compile).value,
    ),
    PB.protoSources in Compile +=
      (baseDirectory in ThisProject).value / "src" / "main" / "protocol",

    libraryDependencies ++= Seq(
      "com.trueaccord.scalapb" %% "scalapb-runtime" % Version.scalapbVersion % "protobuf",
      "io.grpc" % "grpc-all" % Version.grpcJavaVersion,
      "com.trueaccord.scalapb" %% "scalapb-runtime-grpc" % Version.scalapbVersion,
      "io.grpc" % "grpc-netty" % Version.grpcJavaVersion,
    )
  )
}

lazy val grpcPrac = (project in file("."))
  .settings(commonSettings("grpcPrac"))
  .aggregate(main)

lazy val grpcProtocol = (project in file("modules/protocol"))
  .settings(commonSettings("grpcProtocol"))
  .settings(grpcProtocolSettings)

lazy val main = (project in file("modules/main"))
  .enablePlugins(JavaAppPackaging)
  .settings(
    commonSettings("main"),
  )
  .settings(
    packageName in Docker := "grpc-sample",
    version in Docker := libVersion,
    dockerRepository := Some("petitviolet"),
    maintainer in Docker := "petitviolet <mail@petitviolet.net>",
    dockerExposedPorts := List(50051),
    dockerBaseImage := "openjdk:latest",
    // mainClass in Compile := Some("net.petitviolet.prac.grpc.main.server"),
    dockerEntrypoint := List("bin/server"),
    dockerCmd := Nil
  )
  .dependsOn(model, grpcProtocol)

lazy val model = (project in file("modules/model"))
  .settings(commonSettings("model"))
