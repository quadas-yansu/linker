package com.quadas.linker.router

import com.twitter.finagle._
import com.twitter.finagle.thrift.ThriftClientRequest
import com.twitter.util.Future
import org.apache.thrift.protocol.TCompactProtocol

object ThriftRouter extends Router {
  def apply(config: RouterConfig): ListeningServer = {
    val client = Thrift.client
      .withParams(configParams(config)(Thrift.client.params))
      .newService(config.dest, "")

    Thrift.server
      .withParams(configParams(config)(Thrift.server.params))
      .serve(config.bind, ThriftFilter andThen client)
  }

  def configParams(config: RouterConfig)(params: Stack.Params): Stack.Params = {
    import RichStackOps._
    import Routers._
    val thrift = config.thrift
    // format: OFF
    Routers
      .configureParams(config)(params)
      .maybeWith(thrift.flatMap(_.framed).map(Thrift.param.Framed.apply))
      .maybeWith(thrift.flatMap(_.maxReusableBufferSize).map(Thrift.Server.param.MaxReusableBufferSize.apply))
      .maybeWith(thrift.flatMap(_.tTwitterUpgrade).map(Thrift.param.AttemptTTwitterUpgrade.apply))
      .maybeWith(thrift.flatMap(_.protocol).map(mkThriftProtocol))
      .having(Thrift.ThriftImpl.Netty4)
    // format: ON
  }

  def mkThriftProtocol(proto: ThriftProtocol): Thrift.param.ProtocolFactory = {
    val factory = proto match {
      case ThriftProtocols.binary => thrift.Protocols.binaryFactory()
      case ThriftProtocols.compact => new TCompactProtocol.Factory
    }

    Thrift.param.ProtocolFactory(factory)
  }
}

object ThriftFilter
    extends Filter[Array[Byte], Array[Byte], ThriftClientRequest, Array[Byte]] {
  override def apply(request: Array[Byte],
                     service: Service[ThriftClientRequest, Array[Byte]])
    : Future[Array[Byte]] = {
    val req = new ThriftClientRequest(request, false)
    service(req)
  }
}
