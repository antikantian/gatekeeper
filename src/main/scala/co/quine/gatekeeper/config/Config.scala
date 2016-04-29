package co.quine.gatekeeper.config

import com.typesafe.config.ConfigFactory

object Config {
  private val config = ConfigFactory.load()

  private lazy val root = config.getConfig("gatekeeper")

  lazy val version = root.getString("version")
  lazy val listenAddress = root.getString("listen-address")

  object BackendConfig {
    private val backendConfig = root.getConfig("backends")

    lazy val redisHost = backendConfig.getString("redis-host")
    lazy val redisPort = backendConfig.getInt("redis-port")
  }

  object TwitterConfig {
    private val twitterConfig = root.getConfig("twitter")

    lazy val twitterHost = twitterConfig.getString("twitter-host")
    lazy val twitterScheme = twitterConfig.getString("twitter-scheme")
    lazy val twitterVersion = twitterConfig.getString("twitter-version")

    lazy val consumer_key = twitterConfig.getString("consumer-key")
    lazy val consumer_secret = twitterConfig.getString("consumer-secret")
  }

}