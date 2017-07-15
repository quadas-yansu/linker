organization  := "com.quadas"
name          := "linker"
version       := "0.1-SNAPSHOT"
scalaVersion  := "2.12.2"
scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

//resolvers += Resolver.sonatypeRepo("releases")

val finagleV = "6.44.0"
val twitterUtilV = "6.43.0"

libraryDependencies ++= {
  Seq(
    "com.quadas"          %%  "konfig-twitter-util"   % "0.1-M10",
    "com.twitter"         %%  "twitter-server"        % "1.29.0",
    "com.twitter"         %%  "util-slf4j-jul-bridge" % twitterUtilV,
    "org.slf4j"           %   "jcl-over-slf4j"        % "1.7.25",
    "org.slf4j"           %   "log4j-over-slf4j"      % "1.7.25",
    "org.slf4j"           %   "jul-to-slf4j"          % "1.7.25",
    "ch.qos.logback"      %   "logback-classic"       % "1.2.3"
  )
}
