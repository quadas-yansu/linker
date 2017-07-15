package com.twitter.finagle.quadas.linker

object QuadasLinker {
  val failureAccrualFactoryParam =
    com.twitter.finagle.liveness.FailureAccrualFactory.Param
  val traceInitializerFilter =
    com.twitter.finagle.tracing.TraceInitializerFilter
  val httpClientContextFilter =
    com.twitter.finagle.http.filter.ClientContextFilter
  val httpServerContextFilter =
    com.twitter.finagle.http.filter.ServerContextFilter
}
