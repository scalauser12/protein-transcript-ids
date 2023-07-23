package example

import cats.effect.IO
import fs2.Stream
import fs2.compression.Compression
import org.http4s.client.Client
import org.http4s.{Request, Uri}

object HttpClient {
  implicit class ClientOps(client: Client[IO]) {
    def openGzipStream(uri: Uri): Stream[IO, Byte] =
      client
        .stream(Request[IO](uri = uri))
        .flatMap(_.entity.body)
        .through(Compression[IO].gunzip())
        .flatMap(_.content)
  }
}
