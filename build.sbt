ThisBuild / scalaVersion                                   := "2.13.7"
ThisBuild / organization                                   := "dev.usommerl"
ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.5.0"

val v = new {
  val circe   = "0.14.1"
  val ciris   = "2.0.0"
  val http4s  = "0.23.6"
  val odin    = "0.13.0"
  val tapir   = "0.19.0-M13"
  val munit   = "0.7.29"
  val munitCE = "1.0.6"
}

lazy val counteeexporter = project
  .in(file("."))
  .enablePlugins(BuildInfoPlugin, sbtdocker.DockerPlugin)
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
      "com.github.valskalla"        %% "odin-core"                % v.odin,
      "com.github.valskalla"        %% "odin-json"                % v.odin,
      "com.github.valskalla"        %% "odin-slf4j"               % v.odin,
      "com.softwaremill.sttp.tapir" %% "tapir-core"               % v.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server"      % v.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe"         % v.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs"       % v.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml" % v.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-refined"            % v.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui"         % v.tapir,
      "is.cir"                      %% "ciris"                    % v.ciris,
      "is.cir"                      %% "ciris-refined"            % v.ciris,
      "io.circe"                    %% "circe-core"               % v.circe,
      "io.circe"                    %% "circe-generic"            % v.circe,
      "io.circe"                    %% "circe-parser"             % v.circe,
      "io.circe"                    %% "circe-literal"            % v.circe,
      "org.http4s"                  %% "http4s-ember-client"      % v.http4s,
      "org.http4s"                  %% "http4s-ember-server"      % v.http4s,
      "org.http4s"                  %% "http4s-circe"             % v.http4s,
      "org.http4s"                  %% "http4s-dsl"               % v.http4s,
      "org.scalameta"               %% "munit"                    % v.munit   % Test,
      "org.typelevel"               %% "munit-cats-effect-3"      % v.munitCE % Test
    ),
    buildInfoKeys                    := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage                 := organization.value,
    buildInfoOptions ++= Seq[BuildInfoOption](BuildInfoOption.ToMap, BuildInfoOption.BuildTime),
    semanticdbEnabled                := true,
    semanticdbVersion                := scalafixSemanticdb.revision,
    docker / imageNames              := Seq(ImageName(s"ghcr.io/usommerl/${name.value}:$dockerImageTag")),
    docker / dockerfile              := {
      val artifact: File     = assembly.value
      val artifactTargetPath = s"/app/${artifact.name}"
      new Dockerfile {
        from("openjdk:11-slim")
        add(artifact, artifactTargetPath)
        entryPoint("java", "-jar", artifactTargetPath)
      }
    },
    assembly / test                  := (Test / test).value,
    assembly / assemblyMergeStrategy := {
      case "META-INF/maven/org.webjars/swagger-ui/pom.properties" => MergeStrategy.singleOrError
      case x                                                      => (assembly / assemblyMergeStrategy).value(x)
    }
  )

def dockerImageTag: String = {
  import sys.process._
  val regex       = """v\d+\.\d+\.\d+""".r.regex
  val versionTags = "git tag --points-at HEAD".!!.trim.split("\n").filter(_.matches(regex))
  versionTags.sorted(Ordering.String.reverse).headOption.map(_.replace("v", "")).getOrElse("latest")
}
