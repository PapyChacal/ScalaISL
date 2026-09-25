package com.github.papychacal.isl.bindgen

import java.nio.file.{Path, Paths}
import scala.collection.mutable

import org.bytedeco.javacpp.{BytePointer, PointerPointer}
import org.bytedeco.llvm.clang.*
import org.bytedeco.llvm.global.clang

final class ClangExtractor(includeDirs: Seq[Path], projectRoot: Path):
  import clang.*

  private val annotationDefinitions = Seq(
    "__isl_give=__attribute__((annotate(\"isl_give\")))",
    "__isl_keep=__attribute__((annotate(\"isl_keep\")))",
    "__isl_take=__attribute__((annotate(\"isl_take\")))",
    "__isl_null=__attribute__((annotate(\"isl_null\")))",
    "__isl_export=__attribute__((annotate(\"isl_export\")))",
    "__isl_overload=__attribute__((annotate(\"isl_overload\"))) __attribute__((annotate(\"isl_export\")))",
    "__isl_constructor=__attribute__((annotate(\"isl_constructor\"))) __attribute__((annotate(\"isl_export\")))",
    "__isl_subclass(super)=__attribute__((annotate(\"isl_subclass(" + "#super" + ")\"))) __attribute__((annotate(\"isl_export\")))"
  )

  def extractHeader(header: Path): IslModel =
    parse(header, skipBodies = true, onlyIslSymbols = true, mainFileOnly = false)

  def extractSource(source: Path): IslModel =
    parse(source, skipBodies = true, onlyIslSymbols = true, mainFileOnly = true)

  private def parse(path: Path, skipBodies: Boolean, onlyIslSymbols: Boolean, mainFileOnly: Boolean): IslModel =
    val index = clang_createIndex(0, 0)
    val args =
      Seq("-x", "c", "-std=gnu11", "-fparse-all-comments", "-DHAVE_CONFIG_H") ++
        includeDirs.map(p => s"-I${p.toAbsolutePath.normalize}") ++
        annotationDefinitions.map(d => s"-D$d")
    val argv = new PointerPointer[org.bytedeco.javacpp.BytePointer](args.toArray*)
    val flags =
      CXTranslationUnit_KeepGoing |
        CXTranslationUnit_IncludeAttributedTypes |
        CXTranslationUnit_VisitImplicitAttributes |
        (if skipBodies then CXTranslationUnit_SkipFunctionBodies else 0)
    val sourceName = new BytePointer(path.toAbsolutePath.toString)
    val tu = clang_parseTranslationUnit(
      index,
      sourceName,
      argv,
      args.size,
      null.asInstanceOf[CXUnsavedFile],
      0,
      flags
    )
    if tu == null || tu.isNull then
      clang_disposeIndex(index)
      throw new IllegalStateException(s"libclang failed to parse $path")

    try collect(tu, onlyIslSymbols, mainFileOnly)
    finally
      clang_disposeTranslationUnit(tu)
      clang_disposeIndex(index)

  private def collect(tu: CXTranslationUnit, onlyIslSymbols: Boolean, mainFileOnly: Boolean): IslModel =
    val annotations = mutable.Map.empty[Int, mutable.Set[String]]
    val enumValues = mutable.Map.empty[Int, mutable.ArrayBuffer[EnumValue]]
    val functions = mutable.LinkedHashMap.empty[String, FunctionDecl]
    val enums = mutable.LinkedHashMap.empty[String, EnumDecl]
    val typedefs = mutable.LinkedHashMap.empty[String, TypedefDecl]

    val metadataVisitor = new CXCursorVisitor:
      override def call(cursor: CXCursor, parent: CXCursor, data: CXClientData): Int =
        if mainFileOnly && clang_Location_isFromMainFile(clang_getCursorLocation(cursor)) == 0 then
          return CXChildVisit_Continue
        val kind = clang_getCursorKind(cursor)
        val name = cxString(clang_getCursorSpelling(cursor))
        if kind == CXCursor_AnnotateAttr then
          annotations.getOrElseUpdate(clang_hashCursor(parent), mutable.Set.empty) += name
        else if kind == CXCursor_EnumConstantDecl then
          enumValues.getOrElseUpdate(clang_hashCursor(parent), mutable.ArrayBuffer.empty) +=
            EnumValue(name, clang_getEnumConstantDeclValue(cursor), rawComment(cursor))
        CXChildVisit_Recurse

    if !mainFileOnly then
      clang_visitChildren(clang_getTranslationUnitCursor(tu), metadataVisitor, null)

    val declarationVisitor = new CXCursorVisitor:
      override def call(cursor: CXCursor, parent: CXCursor, data: CXClientData): Int =
        if mainFileOnly && clang_Location_isFromMainFile(clang_getCursorLocation(cursor)) == 0 then
          return CXChildVisit_Continue
        val kind = clang_getCursorKind(cursor)
        val name = cxString(clang_getCursorSpelling(cursor))
        val relevant = !onlyIslSymbols || name.startsWith("isl_") || kind == CXCursor_EnumDecl
        if relevant then
          kind match
            case CXCursor_FunctionDecl if name.startsWith("isl_") =>
              val declaration = readFunction(cursor, annotations)
              val key = if declaration.usr.nonEmpty then declaration.usr else declaration.signature
              functions.get(key) match
                case Some(previous) if previous.isDefinition && !declaration.isDefinition => ()
                case _ => functions(key) = declaration
              return CXChildVisit_Continue
            case CXCursor_EnumDecl =>
              readEnum(cursor, enumValues).foreach(e => enums.getOrElseUpdate(e.name, e))
            case CXCursor_TypedefDecl if name.startsWith("isl_") =>
              typedefs.getOrElseUpdate(name, TypedefDecl(
                name,
                readType(clang_getTypedefDeclUnderlyingType(cursor)),
                rawComment(cursor),
                sourceLocation(cursor)
              ))
            case _ => ()
        CXChildVisit_Recurse

    clang_visitChildren(clang_getTranslationUnitCursor(tu), declarationVisitor, null)
    IslModel(functions.values.toVector, enums.values.toVector, typedefs.values.toVector, diagnostics(tu))

  private def readFunction(
      cursor: CXCursor,
      annotationsByCursor: mutable.Map[Int, mutable.Set[String]]
  ): FunctionDecl =
    val annotations = annotationsByCursor.get(clang_hashCursor(cursor)).fold(Set.empty[String])(_.toSet)
    val count = clang_Cursor_getNumArguments(cursor)
    val parameters = Vector.tabulate(math.max(0, count)) { index =>
      val argument = clang_Cursor_getArgument(cursor, index)
      val argumentAnnotations = annotationsByCursor.get(clang_hashCursor(argument)).fold(Set.empty[String])(_.toSet)
      Parameter(
        cxString(clang_getCursorSpelling(argument)) match
          case "" => s"arg${index + 1}"
          case value => value,
        readType(clang_getCursorType(argument)),
        Model.annotationOwnership(argumentAnnotations),
        argumentAnnotations
      )
    }
    FunctionDecl(
      cxString(clang_getCursorUSR(cursor)),
      cxString(clang_getCursorSpelling(cursor)),
      readType(clang_getCursorResultType(cursor)),
      parameters,
      annotations,
      rawComment(cursor),
      sourceLocation(cursor),
      clang_isCursorDefinition(cursor) != 0
    )

  private def readEnum(
      cursor: CXCursor,
      enumValues: mutable.Map[Int, mutable.ArrayBuffer[EnumValue]]
  ): Option[EnumDecl] =
    val directName = cxString(clang_getCursorSpelling(cursor))
    val typeName = cxString(clang_getTypeSpelling(clang_getCursorType(cursor))).stripPrefix("enum ")
    val name = if directName.nonEmpty then directName else typeName
    if !name.startsWith("isl_") then None
    else
      val values = enumValues.get(clang_hashCursor(cursor)).fold(Vector.empty[EnumValue])(_.toVector)
      Some(EnumDecl(name, values, rawComment(cursor), sourceLocation(cursor)))

  private def readType(raw: CXType): CType =
    val spelling = cxString(clang_getTypeSpelling(raw))
    val canonicalType = clang_getCanonicalType(raw)
    val canonical = cxString(clang_getTypeSpelling(canonicalType))
    val kindName = cxString(clang_getTypeKindSpelling(raw.kind()))
    if canonicalType.kind() == CXType_Pointer then
      val pointeeRaw = clang_getPointeeType(canonicalType)
      val pointee = readSimpleType(pointeeRaw)
      if pointeeRaw.kind() == CXType_FunctionProto || pointeeRaw.kind() == CXType_FunctionNoProto then
        val result = readType(clang_getResultType(pointeeRaw))
        val count = clang_getNumArgTypes(pointeeRaw)
        CType(spelling, canonical, kindName, Some(pointee), Some(result),
          Vector.tabulate(math.max(0, count))(i => readType(clang_getArgType(pointeeRaw, i))))
      else CType(spelling, canonical, kindName, Some(pointee))
    else CType(spelling, canonical, kindName)

  private def readSimpleType(raw: CXType): CType =
    CType(
      cxString(clang_getTypeSpelling(raw)),
      cxString(clang_getTypeSpelling(clang_getCanonicalType(raw))),
      cxString(clang_getTypeKindSpelling(raw.kind()))
    )

  private def rawComment(cursor: CXCursor): Option[String] =
    Option(cxString(clang_Cursor_getRawCommentText(cursor))).map(_.trim).filter(_.nonEmpty)

  private def sourceLocation(cursor: CXCursor): SourceLocation =
    val file = new CXFile()
    val line = Array(0)
    val column = Array(0)
    val offset = Array(0)
    clang_getSpellingLocation(clang_getCursorLocation(cursor), file, line, column, offset)
    val path =
      if file == null || file.isNull then projectRoot
      else Paths.get(cxString(clang_getFileName(file)))
    SourceLocation(path, line(0), column(0))

  private def diagnostics(tu: CXTranslationUnit): Vector[Diagnostic] =
    Vector.tabulate(clang_getNumDiagnostics(tu)) { index =>
      val diagnostic = clang_getDiagnostic(tu, index)
      try Diagnostic(cxString(clang_formatDiagnostic(diagnostic, clang_defaultDiagnosticDisplayOptions())), clang_getDiagnosticSeverity(diagnostic))
      finally clang_disposeDiagnostic(diagnostic)
    }

  private def cxString(value: CXString): String =
    if value == null then ""
    else
      try
        val bytes = clang_getCString(value)
        if bytes == null || bytes.isNull then "" else bytes.getString
      finally clang_disposeString(value)
