package xkcd1083

import com.ning.http.client.oauth.{RequestToken, ConsumerKey}
import scala.io.Source
import grizzled.config.Configuration

//* Going to be an ugly error if the config file isn't located at `config`. */
trait HasConfig {
  val config = Configuration(Source.fromFile("config"))
}

/** The twitter APPLICATION's API key. */
trait HasConsumerKey extends HasConfig {
  val consumerKey: ConsumerKey = new ConsumerKey(
    config.get("oauth", "consumer_key").get,
    config.get("oauth", "consumer_secret").get
  )
}

/** The USER's access token information. */
trait HasAccessToken extends HasConfig {
  val accessToken: RequestToken = new RequestToken(
    config.get("oauth", "access_token").get,
    config.get("oauth", "access_token_secret").get
  )
}
