package com.github.papychacal.isl.bindgen

import java.nio.file.Path

enum Ownership:
  case Give, Take, Keep, Null

final case class SourceLocation(file: Path, line: Int, column: Int):
  def display(root: Path): String =
    val absolute = file.toAbsolutePath.normalize
    val base = root.toAbsolutePath.normalize
    val shown = if absolute.startsWith(base) then base.relativize(absolute) else absolute
    s"$shown:$line"

final case class CType(
    spelling: String,
    canonical: String,
    kind: String,
    pointee: Option[CType] = None,
    callbackResult: Option[CType] = None,
    callbackParameters: Vector[CType] = Vector.empty
):
  def isPointer: Boolean = pointee.nonEmpty
  def isCallback: Boolean = callbackResult.nonEmpty

final case class Parameter(
    name: String,
    cType: CType,
    ownership: Option[Ownership],
    annotations: Set[String]
)

final case class FunctionDecl(
    usr: String,
    name: String,
    result: CType,
    parameters: Vector[Parameter],
    annotations: Set[String],
    comment: Option[String],
    location: SourceLocation,
    isDefinition: Boolean,
    definition: Option[SourceLocation] = None
):
  def signature: String =
    s"${result.canonical} ${name}(${parameters.map(_.cType.canonical).mkString(",")})"

  def resultOwnership: Option[Ownership] = Model.annotationOwnership(annotations)
  def exported: Boolean = annotations.contains("isl_export")
  def constructor: Boolean = annotations.contains("isl_constructor")

final case class EnumValue(name: String, value: Long, comment: Option[String])

final case class EnumDecl(
    name: String,
    values: Vector[EnumValue],
    comment: Option[String],
    location: SourceLocation
)

final case class TypedefDecl(
    name: String,
    underlying: CType,
    comment: Option[String],
    location: SourceLocation
)

final case class Diagnostic(message: String, severity: Int)

final case class IslModel(
    functions: Vector[FunctionDecl],
    enums: Vector[EnumDecl],
    typedefs: Vector[TypedefDecl],
    diagnostics: Vector[Diagnostic]
):
  lazy val objectTypes: Set[String] =
    typedefs.iterator
      .filter(t => t.name.startsWith("isl_") && t.underlying.canonical.startsWith("struct isl_"))
      .map(_.name)
      .toSet ++
      functions.iterator.flatMap(f => f.result +: f.parameters.map(_.cType)).flatMap(Model.objectType).toSet

object Model:
  def annotationOwnership(annotations: Set[String]): Option[Ownership] =
    if annotations.contains("isl_give") then Some(Ownership.Give)
    else if annotations.contains("isl_take") then Some(Ownership.Take)
    else if annotations.contains("isl_keep") then Some(Ownership.Keep)
    else if annotations.contains("isl_null") then Some(Ownership.Null)
    else None

  def objectType(cType: CType): Option[String] =
    Option.unless(cType.isCallback)(cType).flatMap(_.pointee)
      .map(_.canonical.stripPrefix("const ").trim)
      .filter(_.matches("struct isl_[A-Za-z0-9_]+"))
      .map(_.stripPrefix("struct "))
