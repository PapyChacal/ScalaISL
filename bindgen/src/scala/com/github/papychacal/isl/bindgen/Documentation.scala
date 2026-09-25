package com.github.papychacal.isl.bindgen

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.Base64
import scala.jdk.CollectionConverters.*
import scala.util.Using

object SourceDocumentation:
  final case class Result(model: IslModel, ambiguities: Vector[String], parsedSources: Int)

  private val documentedDefinition =
    raw"(?s)/\*.*?\*/\s*(?:__isl_[A-Za-z_]+(?:\([^)]*\))?\s+)*(?:[A-Za-z_][A-Za-z0-9_]*\s+|\*\s*)*(isl_[A-Za-z0-9_]+)\s*\(".r

  private final case class DefinitionDoc(signature: String, comment: String, location: SourceLocation)

  def enrich(model: IslModel, projectRoot: Path, islRoot: Path, includeDirs: Seq[Path]): Result =
    val undocumented = model.functions.filter(_.comment.forall(!usefulComment(_)))
    val names = undocumented.iterator.map(_.name).toSet
    if names.isEmpty then Result(model, Vector.empty, 0)
    else
      val candidates = Using.resource(Files.walk(islRoot)) { paths =>
        paths.iterator.asScala
          .filter(path => Files.isRegularFile(path) && path.getFileName.toString.endsWith(".c"))
          // These files are textual implementation fragments included by real translation units.
          .filterNot(path => path.getFileName.toString.contains("templ"))
          .filter { path =>
            val text = Files.readString(path, StandardCharsets.UTF_8)
            documentedDefinition.findAllMatchIn(text).exists(found => names.contains(found.group(1)))
          }
          .toVector
      }

      val extracted = candidates.map(path => path -> extractIsolated(projectRoot, path, includeDirs))
      val failures = extracted.collect { case (path, Left(reason)) =>
        s"unable to extract source documentation from ${projectRoot.relativize(path)}: $reason"
      }
      val definitions = extracted.collect { case (_, Right(values)) => values }.flatten.filter(doc => usefulComment(doc.comment))
      val bySignature = definitions.groupBy(_.signature)
      val ambiguities = bySignature.collect {
        case (signature, matches) if matches.map(_.comment).distinct.size > 1 =>
          s"ambiguous source documentation for $signature: ${matches.map(_.location.display(islRoot)).mkString(", ")}"
      }.toVector.sorted
      val definitionsBySignature = bySignature.collect {
        case (signature, matches) if matches.map(_.comment).distinct.size == 1 => signature -> matches.head
      }
      val enriched = model.copy(functions = model.functions.map { function =>
        if function.comment.exists(usefulComment) then function
        else definitionsBySignature.get(function.signature) match
          case Some(definition) => function.copy(comment = Some(definition.comment), definition = Some(definition.location))
          case None => function
      })
      Result(enriched, ambiguities ++ failures, candidates.size)

  private def extractIsolated(projectRoot: Path, source: Path, includeDirs: Seq[Path]): Either[String, Vector[DefinitionDoc]] =
    val java = Paths.get(System.getProperty("java.home"), "bin", "java").toString
    val command = Seq(java, "-Xint", "-cp", System.getProperty("java.class.path"), "com.github.papychacal.isl.bindgen.SourceWorker",
      projectRoot.toString, source.toString) ++ includeDirs.map(_.toString)
    val process = new ProcessBuilder(command*)
      .redirectError(ProcessBuilder.Redirect.DISCARD)
      .start()
    val output = new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
    val exit = process.waitFor()
    if exit != 0 then Left(s"worker exited with status $exit")
    else
      try Right(output.linesIterator.filter(_.nonEmpty).map(parseDefinition).toVector)
      catch case error: Exception => Left(s"invalid worker output: ${error.getMessage}")

  private def parseDefinition(line: String): DefinitionDoc =
    val fields = line.split("\\t", -1).map(decode)
    DefinitionDoc(fields(0), fields(1), SourceLocation(Paths.get(fields(2)), fields(3).toInt, fields(4).toInt))

  private def decode(value: String): String =
    String(Base64.getUrlDecoder.decode(value), StandardCharsets.UTF_8)

  private def usefulComment(comment: String): Boolean =
    val body = ScaladocFormatter.clean(comment).trim
    body.nonEmpty &&
      body != "struct isl_ctx functions" &&
      !body.startsWith("Copyright ") &&
      !body.startsWith("Use of this software")

object ScaladocFormatter:
  def clean(raw: String): String =
    val withoutDelimiters = raw
      .replaceFirst("(?s)^\\s*/\\*+", "")
      .replaceFirst("(?s)\\*/\\s*$", "")
    withoutDelimiters.linesIterator
      .map(_.replaceFirst("^\\s*\\* ?", "").stripTrailing())
      .mkString("\n")
      .trim

  def format(parts: Seq[String], indent: String = ""): String =
    val body = parts.iterator.map(clean).filter(_.nonEmpty).mkString("\n\n")
      .replace("*/", "*&#47;")
    if body.isEmpty then ""
    else
      val lines = body.linesIterator.toVector
      (Vector(s"$indent/**") ++ lines.map {
        case "" => s"$indent *"
        case line => s"$indent * $line"
      } ++ Vector(s"$indent */")).mkString("\n") + "\n"
