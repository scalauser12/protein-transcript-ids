package example

import cats.effect.IO
import fs2._
import fs2.data.csv.generic.semiauto.deriveRowDecoder
import fs2.data.csv.{RowDecoder, decodeWithoutHeaders}

object TSV {
  private implicit val rowDecoder: RowDecoder[ProteinTranscriptRow] =
    deriveRowDecoder

  def processStream(
      stream: Stream[IO, Byte]
  ): Stream[IO, ProteinTranscriptRow] =
    stream
      .through(text.utf8.decode)
      .through(text.lines)
      .map(transformLine)
      .unNone
      .through(decodeWithoutHeaders[ProteinTranscriptRow]())

  private def transformLine(line: String) = {
    val fields = line.split("\t")
    if (
      fields(0) != "CDS" ||
      fields.length < 16 ||
      (fields(10).isEmpty && fields(12).isEmpty)
    )
      None
    else
      Some(
        Seq(fields(15), fields(14), fields(10), fields(12))
          .mkString("", ",", "\n")
      )
  }
}

final case class ProteinTranscriptRow(
    geneId: Long,
    symbol: String,
    productAccession: String,
    relatedAccession: String
)
