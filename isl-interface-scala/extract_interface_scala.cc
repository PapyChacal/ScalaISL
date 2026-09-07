/*
 * The extractor setup in this file is derived from ISL 0.28's
 * interface/extract_interface.cc.
 *
 * Copyright 2011 Sven Verdoolaege. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY SVEN VERDOOLAEGE "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES ARE DISCLAIMED. IN NO EVENT SHALL SVEN VERDOOLAEGE
 * OR CONTRIBUTORS BE LIABLE FOR ANY DAMAGES ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE.
 */

#include "isl_config.h"
#undef PACKAGE

#include <assert.h>
#include <iostream>
#include <memory>
#include <stdlib.h>
#include <type_traits>

#include <llvm/Support/CommandLine.h>
#include <llvm/Support/ManagedStatic.h>
#include <llvm/Support/raw_ostream.h>
#include <clang/AST/ASTConsumer.h>
#include <clang/Basic/DiagnosticOptions.h>
#include <clang/Basic/FileSystemOptions.h>
#include <clang/Frontend/CompilerInstance.h>
#include <clang/Frontend/TextDiagnosticPrinter.h>
#include <clang/Frontend/Utils.h>
#include <clang/Lex/HeaderSearch.h>
#include <clang/Lex/Preprocessor.h>
#include <clang/Lex/PreprocessorOptions.h>
#include <clang/Parse/ParseAST.h>
#include <clang/Sema/Sema.h>

#include "isl-interface/clang_wrap.h"

#include "extract_interface.h"
#include "scala.h"

using namespace std;
using namespace clang;
using namespace clang::driver;

static llvm::cl::opt<string> InputFilename(llvm::cl::Positional,
	llvm::cl::Required, llvm::cl::desc("<input file>"));
static llvm::cl::list<string> Includes("I",
	llvm::cl::desc("Header search path"), llvm::cl::value_desc("path"),
	llvm::cl::Prefix);
static llvm::cl::opt<string> OutputLanguage("language", llvm::cl::Required,
	llvm::cl::ValueRequired, llvm::cl::desc("Bindings to generate"),
	llvm::cl::value_desc("name"));

bool has_annotation(Decl *decl, const char *name)
{
	if (!decl->hasAttrs())
		return false;

	AttrVec attrs = decl->getAttrs();
	for (AttrVec::const_iterator i = attrs.begin(); i != attrs.end(); ++i) {
		const AnnotateAttr *ann = dyn_cast<AnnotateAttr>(*i);
		if (ann && ann->getAnnotation().str() == name)
			return true;
	}
	return false;
}

static bool is_exported(Decl *decl)
{
	return has_annotation(decl, "isl_export");
}

struct ScalaASTConsumer : public ASTConsumer {
	set<RecordDecl *> exported_types;
	set<FunctionDecl *> exported_functions;
	set<FunctionDecl *> functions;

	virtual HandleTopLevelDeclReturn HandleTopLevelDecl(DeclGroupRef D) {
		if (!D.isSingleDecl())
			return HandleTopLevelDeclContinue;

		Decl *decl = D.getSingleDecl();
		if (isa<FunctionDecl>(decl))
			functions.insert(cast<FunctionDecl>(decl));
		if (!is_exported(decl))
			return HandleTopLevelDeclContinue;

		switch (decl->getKind()) {
		case Decl::Record:
			exported_types.insert(cast<RecordDecl>(decl));
			break;
		case Decl::Function:
			exported_functions.insert(cast<FunctionDecl>(decl));
			break;
		default:
			break;
		}
		return HandleTopLevelDeclContinue;
	}
};

struct ScalaExtractor : public isl::clang::Wrap {
	virtual TextDiagnosticPrinter *construct_printer() override;
	virtual void suppress_errors(DiagnosticsEngine &Diags) override;
	virtual void add_paths(HeaderSearchOptions &HSO) override;
	virtual void add_macros(PreprocessorOptions &PO) override;
	virtual void handle_error() override;
	virtual bool handle(CompilerInstance *Clang) override;
};

TextDiagnosticPrinter *ScalaExtractor::construct_printer()
{
	return new TextDiagnosticPrinter(llvm::errs(), getDiagnosticOptions());
}

void ScalaExtractor::suppress_errors(DiagnosticsEngine &Diags)
{
}

void ScalaExtractor::add_paths(HeaderSearchOptions &HSO)
{
	for (llvm::cl::list<string>::size_type i = 0; i < Includes.size(); ++i)
		isl::clang::add_path(HSO, Includes[i]);
}

void ScalaExtractor::add_macros(PreprocessorOptions &PO)
{
	PO.addMacroDef("__isl_give=__attribute__((annotate(\"isl_give\")))");
	PO.addMacroDef("__isl_keep=__attribute__((annotate(\"isl_keep\")))");
	PO.addMacroDef("__isl_take=__attribute__((annotate(\"isl_take\")))");
	PO.addMacroDef("__isl_export=__attribute__((annotate(\"isl_export\")))");
	PO.addMacroDef("__isl_overload=__attribute__((annotate(\"isl_overload\"))) "
		"__attribute__((annotate(\"isl_export\")))");
	PO.addMacroDef("__isl_constructor=__attribute__((annotate(\"isl_constructor\"))) "
		"__attribute__((annotate(\"isl_export\")))");
	PO.addMacroDef("__isl_subclass(super)=__attribute__((annotate(\"isl_subclass(\" #super \"\"))) "
		"__attribute__((annotate(\"isl_export\")))");
}

void ScalaExtractor::handle_error()
{
	assert(false);
}

bool ScalaExtractor::handle(CompilerInstance *Clang)
{
	Preprocessor &PP = Clang->getPreprocessor();
	ScalaASTConsumer consumer;
	Sema *sema = new Sema(PP, Clang->getASTContext(), consumer);

	DiagnosticsEngine &Diags = Clang->getDiagnostics();
	Diags.getClient()->BeginSourceFile(Clang->getLangOpts(), &PP);
	ParseAST(*sema);
	Diags.getClient()->EndSourceFile();

	scala_generator generator(Clang->getSourceManager(),
		consumer.exported_types, consumer.exported_functions,
		consumer.functions);
	generator.generate();

	delete sema;
	return !Diags.hasErrorOccurred();
}

int main(int argc, char *argv[])
{
	llvm::cl::ParseCommandLineOptions(argc, argv);
	if (OutputLanguage.compare("scala-3") != 0) {
		cerr << "Language '" << OutputLanguage << "' not recognized; "
		     << "this extractor only supports 'scala-3'." << endl;
		return EXIT_FAILURE;
	}

	ScalaExtractor extractor;
	bool ok = extractor.invoke(InputFilename.c_str());
	llvm::llvm_shutdown();
	return ok ? EXIT_SUCCESS : EXIT_FAILURE;
}
