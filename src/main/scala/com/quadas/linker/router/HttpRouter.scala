package com.quadas.linker.router

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle._
import com.twitter.finagle.quadas.linker.QuadasLinker
import com.twitter.util.{Future, Return, Throw}


object HttpRouter extends Router {
  def apply(config: RouterConfig): ListeningServer = {
    val client = Http.client
      .withParams(configParams(config)(Http.client.params))
      .withStack(clientStack(config)(Http.client.stack))
      .filtered(new LinkerHttpRouterOverrideHostFilter(
              config.http.flatMap(_.hostHeader)))
      .newService(config.dest, "")

    Http.server
      .withParams(configParams(config)(Http.server.params))
      .withStack(serverStack(config)(Http.server.stack))
      .serve(config.bind, client)
  }

  def serverStack[T](config: RouterConfig)(stack: Stack[T]): Stack[T] = {
    import RichStackOps._
    val external = config.external.flatMap(_.server).contains(true)
    stack
      .mayRemove(external)(QuadasLinker.traceInitializerFilter.role) // trace
      .mayRemove(external)(QuadasLinker.httpServerContextFilter.role) // deadline, retries
  }

  def clientStack[T](config: RouterConfig)(stack: Stack[T]): Stack[T] = {
    import RichStackOps._
    val external = config.external.flatMap(_.client).contains(true)
    stack
      .mayRemove(external)(QuadasLinker.traceInitializerFilter.role) // trace
      .mayRemove(external)(QuadasLinker.httpClientContextFilter.role) // deadline, retries
  }

  def configParams(config: RouterConfig)(params: Stack.Params): Stack.Params = {
    import com.twitter.finagle.http.{param => httpparam}
    import RichStackOps._
    import Routers._

    val http = config.http

    // format: OFF
    Routers
      .configureParams(config)(params)
      .maybeWith(http.flatMap(_.streaming).map(httpparam.Streaming.apply))
      .maybeWith(http.flatMap(_.maxHeaderSize).map(httpparam.MaxHeaderSize.apply))
      .maybeWith(http.flatMap(_.maxInitialLineSize).map(httpparam.MaxInitialLineSize.apply))
      .maybeWith(http.flatMap(_.maxRequestSize).map(httpparam.MaxRequestSize.apply))
      .maybeWith(http.flatMap(_.maxResponseSize).map(httpparam.MaxResponseSize.apply))
      .maybeWith(http.flatMap(_.maxChunkSize).map(httpparam.MaxChunkSize.apply))
      .maybeWith(http.flatMap(_.decompression).map(httpparam.Decompression.apply))
      .maybeWith(http.flatMap(_.compressionLevel).map(httpparam.CompressionLevel.apply))
      .having(Http.Netty4Impl)
  }
  // format: ON

}

class LinkerHttpRouterOverrideHostFilter(maybeHost: Option[String])
    extends SimpleFilter[Request, Response] {
  override def apply(request: Request,
                     service: Service[Request, Response]): Future[Response] = {
    maybeHost.foreach(host => request.host = host)
    service(request)
  }
}
