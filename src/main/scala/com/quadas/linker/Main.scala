package com.quadas.linker

import com.quadas.linker.router._
import com.twitter.server.TwitterServer
import com.twitter.util.Await
import com.typesafe.config.ConfigFactory

object Main extends TwitterServer {

  def main(): Unit = {
    val linkerConfig = RouterConfigReader.read(ConfigFactory.load(), "linker")

    val routers = linkerConfig.routers
      .map { rc =>
        log.info(s"Initializing router ${rc.name}")
        val server = Router(rc)
        closeOnExit(server)
        server
      }

    Await.all(routers: _*)
  }
}

