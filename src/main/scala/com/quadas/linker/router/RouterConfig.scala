package com.quadas.linker.router

import com.twitter.util.{Duration, StorageUnit}

case class LinkerConfig(
    routers: List[RouterConfig]
)

case class RouterConfig(
    name: String, // label
    `type`: RouterProtocol,
    bind: String,
    dest: String,
    // **flat**
    misc: MiscConfig,
    // **flat**
    general: GeneralConfig,
    external: Option[ExternalConfig],
    http: Option[HttpConfig],
    thrift: Option[ThriftConfig]
)

case class MiscConfig(
    stats: Option[Boolean], // default: true
    trace: Option[Boolean] // default: true
)

case class GeneralConfig(
    timeout: Option[TimeoutConfig],
    admission: Option[AdmissionControl],
    session: Option[Session],
    retry: Option[Retry],
    loadbalancer: Option[LoadBalancer]
)

case class ThriftConfig(
    framed: Option[Boolean],
    protocol: Option[ThriftProtocol],
    tTwitterUpgrade: Option[Boolean],
    maxReusableBufferSize: Option[Int]
)

case class HttpConfig(
    hostHeader: Option[String],
    streaming: Option[Boolean],
    maxHeaderSize: Option[StorageUnit],
    maxInitialLineSize: Option[StorageUnit],
    maxRequestSize: Option[StorageUnit],
    maxResponseSize: Option[StorageUnit],
    maxChunkSize: Option[StorageUnit],
    decompression: Option[Boolean],
    compressionLevel: Option[Int]
)

sealed trait RouterProtocol

object RouterProtocols {

  case object http extends RouterProtocol

  case object thrift extends RouterProtocol


}

sealed trait ThriftProtocol

object ThriftProtocols {

  case object compact extends ThriftProtocol

  case object binary extends ThriftProtocol

}

case class TimeoutConfig(
    total: Option[Duration],
    connect: Option[Duration]
)

case class ExternalConfig(
    client: Option[Boolean],
    server: Option[Boolean]
)

case class AdmissionControl(
    maxPendingRequests: Option[Int], // client
    maxConcurrentRequests: Option[Int], // server
    maxWaiters: Option[Int] // server
)

case class Session(
    acquisitionTimeout: Option[Duration], // client
    maxIdleTime: Option[Duration], // server
    maxLifeTime: Option[Duration], // server
    pool: Option[SessionPool],
    qualifier: Option[SessionQualifier]
)

case class SessionPool(
    maxSize: Option[Int],
    minSize: Option[Int],
    maxWaiters: Option[Int]
)

case class Retry(
    budget: RetryBudget,
    backoff: Backoff
)

case class RetryBudget(
    ttl: Duration,
    minRetriesPerSec: Int,
    percentCanRetry: Double
)

sealed trait Backoff

object Backoffs {
  case class constant(duration: Duration) extends Backoff
  case class jittered(min: Duration, max: Duration) extends Backoff

}

sealed trait LoadBalancer {
  def enableProbation: Option[Boolean]
}

object LoadBalancers {

  case class p2c(enableProbation: Option[Boolean], maxEffort: Option[Int])
      extends LoadBalancer

  case class ewma(enableProbation: Option[Boolean],
                  decayTime: Option[Duration],
                  maxEffort: Option[Int])
      extends LoadBalancer

  case class aperture(
      enableProbation: Option[Boolean],
      smoothWindow: Option[Duration],
      maxEffort: Option[Int],
      lowLoad: Option[Double],
      highLoad: Option[Double],
      minAperture: Option[Int]
  ) extends LoadBalancer

  case class heap(enableProbation: Option[Boolean]) extends LoadBalancer

  case class roundRobin(enableProbation: Option[Boolean],
                        maxEffort: Option[Int])
      extends LoadBalancer

}

case class SessionQualifier(
    failFast: Option[Boolean],
    failureAccrual: Option[FailureAccrual]
)

sealed trait FailureAccrual

object FailureAccruals {

  case object none extends FailureAccrual

  case class consecutiveFailures(failures: Int, backoff: Backoff)
      extends FailureAccrual

  case class successRate(rate: Double, requests: Int, backoff: Backoff)
      extends FailureAccrual

  case class successRateWindowed(rate: Double,
                                 window: Duration,
                                 backoff: Backoff)
      extends FailureAccrual

}

