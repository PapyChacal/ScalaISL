#ifndef ISL_INTERFACE_SCALA_H
#define ISL_INTERFACE_SCALA_H

#include <iostream>
#include <string>
#include <vector>

#include "generator.h"

class scala_generator : public generator {
protected:
	struct class_printer;
	std::map<std::string, FunctionProtoType*> callback_types;
public:
	scala_generator(SourceManager &SM, set<RecordDecl *> &exported_types,
		set<FunctionDecl *> exported_functions,
		set<FunctionDecl *> functions);

    void generate() override;
    std::string isl_type_to_scala(const QualType &type);
	void printParametersList(std::ostream &os, ArrayRef<ParmVarDecl*> parameters);
	void printFunctionParameters(std::ostream &os, const FunctionDecl *f, bool use_given);
	void printMethodParameters(std::ostream &os, const FunctionDecl *f);
	void printLibraryCall(std::ostream &os, const FunctionDecl *f, bool as_method);
	void printId(std::ostream &os, const std::string& id);
	std::string prototype_to_scala(const FunctionProtoType *ft);
};

#endif /* ISL_INTERFACE_SCALA_H */