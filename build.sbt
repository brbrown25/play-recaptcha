name := "play-recaptcha"

description := "Google reCAPTCHA integration for Play Framework"

organization := "com.bbrownsound"

version := "1.1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

crossScalaVersions := Seq("2.11.6", "2.10.4")

libraryDependencies ++= Seq(
  ws,
  "org.mockito" % "mockito-core" % "1.+" % "test"
)

// latest sbt-gpg plugin needs to know these explicitly
pgpSecretRing := file("/Users/brown/.sbt/gpg/secring.asc")

pgpPublicRing := file("/Users/brown/.sbt/gpg/pubring.asc")

// adds "test-conf" to the test classpath (for message resolving)
unmanagedClasspath in Test <+= baseDirectory map { bd => Attributed.blank(bd / "test-conf") }

// needed to publish to maven central
publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>https://github.com/brbrown25/play-recaptcha</url>
  <licenses>
    <license>
      <name>Apache License, Version 2.0</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <connection>scm:git:git@github.com:brbrown25/play-recaptcha.git</connection>
    <developerConnection>scm:git:git@github.com:brbrown25/play-recaptcha.git</developerConnection>
    <url>git@github.com:brbrown25/play-recaptcha.git</url>
  </scm>
  <developers>
    <developer>
      <id>bbrownsound</id>
      <name>Brandon Brown</name>
      <email>brandon@bbrownsound.com</email>
      <timezone>UTC</timezone>
    </developer>
  </developers>)
  