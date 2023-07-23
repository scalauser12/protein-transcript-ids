package example

import cats.effect.IO
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.util.fragment.Fragment

object SQL {
  def insertRow(
      table: String,
      row: ProteinTranscriptRow
  )(implicit transactor: Transactor[IO]): IO[Unit] = {
    val transcriptIds = s"${row.productAccession},${row.relatedAccession}"
    val sql = fr"insert into " ++
      Fragment.const(table) ++
      fr"values (${row.geneId}, ${row.symbol}, $transcriptIds)"
    sql.update.run.transact(transactor).void
  }

  def dropAndCreateTranscriptTable(
      name: String
  )(implicit transactor: Transactor[IO]): IO[Unit] =
    (dropTable(name), createTranscriptTable(name))
      .mapN(_ + _)
      .transact(transactor)
      .void

  def dropAndCreateProteinIdsTable(
      name: String
  )(implicit transactor: Transactor[IO]): IO[Unit] =
    (dropTable(name), createProteinIdsTable(name))
      .mapN(_ + _)
      .transact(transactor)
      .void

  def populateProteinIdsTable(
      proteinIdsTableName: String,
      dataTableName: String,
      taxonomyId: Int
  )(implicit transactor: Transactor[IO]): IO[Unit] = (fr"insert into " ++
    Fragment.const(
      proteinIdsTableName
    ) ++ fr"(gene_id, symbol, transcript_ids, taxonomy_id)" ++
    fr"""select gene_id, symbol, string_agg(transcript_ids, '|'), """ ++
    Fragment.const(taxonomyId.toString) ++
    fr"from " ++
    Fragment.const(dataTableName) ++
    fr"group by gene_id, symbol").update.run.transact(transactor).void

  def dropAndCreateMappingTable(name: String)(implicit
      transactor: Transactor[IO]
  ): IO[Unit] =
    (dropTable(name), createMappingTable(name))
      .mapN(_ + _)
      .transact(transactor)
      .void

  def populateMappingTable(
      mappingTableName: String,
      proteinIdsTableName: String
  )(implicit transactor: Transactor[IO]): IO[Unit] =
    (fr"insert into" ++
      Fragment.const(mappingTableName) ++
      fr"(context, mapping_from, mapping_to) " ++
      fr"select 'gene_to_protein_transcript', gene_id, transcript_ids from " ++
      Fragment.const(proteinIdsTableName)).update.run.transact(transactor).void

  private def dropTable(name: String) =
    (fr"drop table if exists " ++ Fragment.const(name)).update.run

  private def createTranscriptTable(name: String) =
    (fr"create table " ++
      Fragment.const(name) ++
      fr"(gene_id bigint, symbol text, transcript_ids text)").update.run

  private def createProteinIdsTable(name: String) =
    (fr"create table " ++
      Fragment.const(name) ++
      fr"(gene_id bigint, symbol text, transcript_ids text, taxonomy_id integer)").update.run

  private def createMappingTable(name: String) =
    (fr"create table " ++
      Fragment.const(name) ++
      fr"(context text, mapping_from bigint, mapping_to text)").update.run

}
