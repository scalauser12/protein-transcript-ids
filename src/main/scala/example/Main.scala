package example

import cats.effect._
import cats.implicits._
import com.zaxxer.hikari.HikariConfig
import doobie.Transactor
import doobie.hikari.HikariTransactor
import example.HttpClient.ClientOps
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.implicits.http4sLiteralsSyntax
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

object Main extends IOApp.Simple {
  override def run: IO[Unit] = (hikariTransactor, emberClient).tupled use {
    case (transactor, httpClient) =>
      implicit val implicitTransactor: Transactor[IO] = transactor
      val humanDataIO = TSV
        .processStream(httpClient.openGzipStream(humanDataUri))
        .evalMap(SQL.insertRow(humanDataTableName, _))
        .compile
        .drain
      val mouseDataIO = TSV
        .processStream(httpClient.openGzipStream(mouseDataUri))
        .evalMap(SQL.insertRow(mouseDataTableName, _))
        .compile
        .drain
      val ratDataIO = TSV
        .processStream(httpClient.openGzipStream(ratDataUri))
        .evalMap(SQL.insertRow(ratDataTableName, _))
        .compile
        .drain

      for {
        _ <- SQL.dropAndCreateTranscriptTable(humanDataTableName)
        _ <- SQL.dropAndCreateTranscriptTable(mouseDataTableName)
        _ <- SQL.dropAndCreateTranscriptTable(ratDataTableName)
        _ <- (humanDataIO, mouseDataIO, ratDataIO).parTupled.void
        _ <- SQL.dropAndCreateProteinIdsTable(proteinIdsTableName)
        _ <- SQL.populateProteinIdsTable(
          proteinIdsTableName,
          humanDataTableName,
          humanTaxonomyId
        )
        _ <- SQL.populateProteinIdsTable(
          proteinIdsTableName,
          mouseDataTableName,
          mouseTaxonomyId
        )
        _ <- SQL.populateProteinIdsTable(
          proteinIdsTableName,
          ratDataTableName,
          ratTaxonomyId
        )
        _ <- SQL.dropAndCreateMappingTable(mappingTableName)
        _ <- SQL.populateMappingTable(mappingTableName, proteinIdsTableName)
      } yield ()
  }

  private val humanDataTableName = "protein_transcripts_human"
  private val humanTaxonomyId = 9606

  private val mouseDataTableName = "protein_transcripts_mouse"
  private val mouseTaxonomyId = 10090

  private val ratDataTableName = "protein_transcripts_rat"
  private val ratTaxonomyId = 10116

  private val proteinIdsTableName = "protein_ids"
  private val mappingTableName = "mapping"

  private val hikariTransactor = for {
    hikariConfig <- Resource.eval(
      IO {
        val user = sys.env.getOrElse(
          "POSTGRES_USER",
          throw new RuntimeException(
            "environment variable POSTGRES_USER not set"
          )
        )
        val passwd = sys.env.getOrElse(
          "POSTGRES_PASSWORD",
          throw new RuntimeException(
            "environment variable POSTGRES_PASSWORD not set"
          )
        )
        val config = new HikariConfig()
        config.setDriverClassName("org.postgresql.Driver")
        // the DB name is ProteinTranscripts
        config.setJdbcUrl("jdbc:postgresql:ProteinTranscripts")
        config.setUsername(user)
        config.setPassword(passwd)
        config
      }
    )
    transactor <- HikariTransactor.fromHikariConfig[IO](hikariConfig)
  } yield transactor

  private implicit val loggerFactory: LoggerFactory[IO] =
    Slf4jFactory.create[IO]
  private val emberClient = EmberClientBuilder.default[IO].build

  private val humanDataUri =
    uri"https://ftp.ncbi.nlm.nih.gov/refseq/H_sapiens/annotation/annotation_releases/current/GCF_000001405.40-RS_2023_03/GCF_000001405.40_GRCh38.p14_feature_table.txt.gz"
  private val mouseDataUri =
    uri"https://ftp.ncbi.nlm.nih.gov/refseq/M_musculus/annotation_releases/current/GCF_000001635.27-RS_2023_04/GCF_000001635.27_GRCm39_feature_table.txt.gz"
  private val ratDataUri =
    uri"https://ftp.ncbi.nlm.nih.gov/refseq/R_norvegicus/annotation_releases/current/GCF_015227675.2-RS_2023_06/GCF_015227675.2_mRatBN7.2_feature_table.txt.gz"
}
