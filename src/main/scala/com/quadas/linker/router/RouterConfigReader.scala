package com.quadas.linker.router

import com.typesafe.config.{Config, ConfigException}

import com.quadas.konfig._
import com.quadas.konfig.twitterutil._


object RouterConfigReader {
  implicit val keyStyle = KeyStyle.Same

  implicit val thriftProtocolR = ConfigReader.fromString[ThriftProtocol] {
    case "binary" => ThriftProtocols.binary
    case "compact" => ThriftProtocols.compact
    case unsupported =>
      throw new ConfigException.Generic(
          s"Unsupported thrift protocol '$unsupported'")
  }

  implicit val routerProtocolR = ConfigReader.fromString[RouterProtocol] {
    case "http" => RouterProtocols.http
    case "thrift" => RouterProtocols.thrift
  }


  val miscConfigR0 = deriveConfigReader[MiscConfig]
  val generalConfigR0 = deriveConfigReader[GeneralConfig]

  // these 2 are not nested in configuration
  implicit val miscR = ConfigReader.mkFlat(miscConfigR0)
  implicit val generalR = ConfigReader.mkFlat(generalConfigR0)

  implicit val routerConfigR = deriveConfigReader[RouterConfig]
  implicit val linkerConfigR = deriveConfigReader[LinkerConfig]

  def read(config: Config, path: String): LinkerConfig =
    config.read[LinkerConfig](path)
}
