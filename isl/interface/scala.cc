#include "scala.h"

/* Collect all functions that belong to a certain type, separating
 * constructors from methods that set an enum value,
 * methods that set a persistent callback and
 * from regular methods, while keeping track of the _to_str,
 * _copy and _free functions, if any, separately.
 * Methods that accept any enum arguments that are not specifically handled
 * are not supported.
 * If there are any overloaded
 * functions, then they are grouped based on their name after removing the
 * argument type suffix.
 * Check for functions that describe subclasses before considering
 * any other functions in order to be able to detect those other
 * functions as belonging to the subclasses.
 * Sort the names of the functions based on their lengths
 * to ensure that nested subclasses are handled later.
 *
 * Also extract information about automatic conversion functions.
 */
scala_generator::scala_generator(SourceManager &SM, set<RecordDecl *> &exported_types,
	set<FunctionDecl *> exported_functions, set<FunctionDecl *> functions) :
        generator(SM, exported_types, exported_functions, functions)
{
	const std::vector<std::string> extra_classes = {
        "isl_ctx",
        "isl_printer",
        "isl_access_info",
        "isl_local_space",
        "isl_args",
        "isl_ast_expr_list",
        "isl_flow",
        "isl_ast_print_options",
        "isl_mat",
        "isl_basic_map_list",
        "isl_map_list",
        "isl_union_map_list",
        "isl_basic_set_list",
        "isl_set_list",
        "isl_union_set_list",
        "isl_union_pw_multi_aff_list",
        "isl_vec",
        "isl_stride_info",
        "isl_restriction",
    };

    for (const auto &class_name : extra_classes) {
        isl_class cls;
        cls.name = class_name;
        cls.superclass_name = "";
        cls.subclass_name = class_name;
        cls.type = nullptr;
        cls.fn_copy = nullptr;
        cls.fn_free = nullptr;
        cls.fn_to_str = nullptr;
        cls.fn_type = nullptr;

        for(const auto& f : functions) {
            if(f->getName() == "isl_ctx_parse_options")
                continue;
            if(f->getName() == "isl_mat_left_hermite")
                continue;
            if(f->getName().substr(0, class_name.size()) != class_name)
                continue;
            if(f->getReturnType().getAsString() == class_name + " *" && std::none_of(f->parameters().begin(), f->parameters().end(), [class_name](const ParmVarDecl *p) {
                return p->getType()->isPointerType() && p->getType()->getPointeeType().getAsString() == class_name;
            })) {
                cls.constructors.insert(f);
                continue;
            }
                
            auto methodName = f->getNameAsString().substr(class_name.size() + 1);

            if(methodName == "copy") {
                cls.fn_copy = f;
                continue;
            }
            if(methodName == "free") {
                cls.fn_free = f;
                continue;
            }
            if(methodName == "to_str") {
                cls.fn_to_str = f;
                continue;
            }
            if(cls.methods.find(methodName) == cls.methods.end()) {
                cls.methods[methodName] = {};
            }
            cls.methods[methodName].insert(f);
        }

        this->classes[class_name] = cls;

    }
}

std::string to_camel_case(std::string s) noexcept {
    bool tail = false;
    std::size_t n = 0;
    for (unsigned char c : s) {
        if (c == '-' || c == '_') {
            tail = false;
        } else if (tail) {
            s[n++] = c;
        } else {
            tail = true;
            s[n++] = std::toupper(c);
        }
    }
    s.resize(n);
    return s;
}

std::string to_snake_case(std::string s) noexcept {
    bool tail = true;
    std::size_t n = 0;
    for (unsigned char c : s) {
        if (c == '-' || c == '_') {
            tail = false;
        } else if (tail) {
            s[n++] = c;
        } else {
            tail = true;
            s[n++] = std::toupper(c);
        }
    }
    s.resize(n);
    return s;
}

std::string isl_class_to_scala(const std::string &s) {
    if(s.substr(0, 4) != "isl_") {
        std::cerr << "Invalid isl class name: `" << s << "`" << std::endl;
        exit(1);
    }
    return to_camel_case(s.substr(4));
}

std::string scala_generator::prototype_to_scala(const FunctionProtoType *ft) {
    std::string name = isl_type_to_scala(ft->getReturnType());
    for (const auto &arg : ft->param_types()) {
        name += isl_type_to_scala(arg);
    }
    return name + "Callback";
}

std::string scala_generator::isl_type_to_scala(const QualType &type, const bool for_jni) {
    // TODO ENUMS CLEAER
    if (type.getAsString().substr(0, 5) == "enum ")
        return "Int";
    if (type.getAsString() == "isl_bool")
        return "Int";
    if (type.getAsString() == "isl_bool *")
        return "IntByReference";
    if (type.getAsString() == "isl_stat")
        return "Int";
    if (type.getAsString() == "FILE *")
        return "File";
    
    if (type.getAsString() == "void")
        return "Unit";
    if (type.getAsString() == "int")
        return "Int";
    if (type.getAsString() == "isl_size")
        return "Int";
    if (type.getAsString() == "uint32_t")
        return "Int @u_int32_t";
    if (type.getAsString() == "char")
        return "Byte";
    if (type.getAsString() == "long")
        return "Long";
    if (type.getAsString() == "double")
        return "Double";
    if (type.getAsString() == "size_t")
        return "Long @size_t";
    if (type.getAsString() == "int *")
        return "IntByReference";
    if (type.getAsString() == "void *")
        return "Pointer";
    if (type.getAsString() == "const void *")
        return "Pointer";
    if (type.getAsString() == "unsigned int")
        return "Int @u_int32_t";
    if (type.getAsString() == "unsigned long")
        return "Long @u_int64_t";
    if (type.getAsString() == "const char *")
        return "String";
    if (type.getAsString() == "char *")
        return "String";
    

    if(type->isPointerType()) {
        auto t = type->getPointeeType();
        if (t->isPointerType())
            if(for_jni)
                return "PointerByReference";
            else
                return "AbstractReference[" + isl_type_to_scala(t) + "]";
        if(t->isFunctionProtoType()) {
            auto ft = t->getAs<FunctionProtoType>();
            callback_types[prototype_to_scala(ft)] = const_cast<FunctionProtoType*>(ft);
            return prototype_to_scala(ft);
        }
        if (for_jni)
            return "Pointer";
        if (t.getAsString().substr(0, 7) == "struct ")
            return isl_class_to_scala(t.getAsString().substr(7));
        if (t.getAsString().substr(0, 6) == "const ")
            return isl_class_to_scala(t.getAsString().substr(6));
        return isl_class_to_scala(t.getAsString());
    }
    std::cerr << "Invalid type name: `" + type.getAsString() + "`" << std::endl;
    exit(1);
}

void scala_generator::printParametersList(std::ostream &os, ArrayRef<ParmVarDecl*> parameters, const bool for_jni) {

    // if (parameters.empty())
    //     return;

    os << "(";
    for (const auto&p : parameters) {
            os << std::endl << "    ";
            printId(os, p->getNameAsString());
            os << ": " << isl_type_to_scala(p->getType(), for_jni);
            if( p != *(parameters.end() - 1))
                os << ",";
            else
                os << std::endl << "  ";
        }
    os << ")";

}

void scala_generator::printFunctionParameters(std::ostream &os, const FunctionDecl *f, bool use_given, const bool for_jni) {
    auto parameters = f->parameters();
    if(use_given && first_arg_is_isl_ctx(const_cast<FunctionDecl*>(f))) {
        os << "(using ctx: Ctx)";
        parameters = parameters.drop_front();
    }
    
    printParametersList(os, parameters, for_jni);
    os << ": " << isl_type_to_scala(f->getReturnType(), for_jni);
}

void scala_generator::printId(std::ostream &os, const std::string& id) {
    bool escape = id == "type" || id == "val";
    if(escape){
        os << "`";
    }
    os << id;
    if(escape){
        os << "`";
    }
}

void scala_generator::printMethodParameters(std::ostream &os, const FunctionDecl *f) {
    auto parameters = f->parameters().drop_front();
    printParametersList(os, parameters);
    os << ": " << isl_type_to_scala(f->getReturnType());
}

void scala_generator::printLibraryCall(std::ostream &os, const FunctionDecl *f, bool as_method) {
    os << "    lib." << f->getNameAsString() << "(";
    auto parameters = f->parameters();

    if(!parameters.empty()) {

        std::vector<std::string> pnames;
        pnames.reserve(parameters.size());

        for(const auto& p : parameters) {
            pnames.push_back(p->getNameAsString());
        }

        if(as_method) {
            pnames[0] = "this";
        }

        for(unsigned i = 0; i < pnames.size(); i++) {
            if(i != 0)
                os << ", ";
            printId(os, pnames[i]);
            if(takes(parameters[i])) {
                auto class_name = parameters[i]->getType()->getPointeeType().getAsString();
                if(classes[class_name].fn_copy != nullptr) {
                    os << ".copy()";
                } else {
                    std::cerr << "Warning: " << class_name << " does not have a copy function" << std::endl;
                    std::cerr << "Warning: " << f->getNameAsString() << " may not work as expected" << std::endl;
                }
            }
        }
    }
    os << ")";
}

void scala_generator::generate()
{
    ostream &os = std::cout;

    os << "package com.github.papychacal.isl" << std::endl
       << std::endl
       << "import scala.annotation.targetName" << std::endl
       << std::endl
       << "import jnr.ffi.*" << std::endl
       << "import jnr.ffi.types.*" << std::endl
       << "import jnr.ffi.byref.*" << std::endl
       << "import jnr.ffi.annotations.Delegate" << std::endl << std::endl
       << "class File(val p: Pointer) extends AnyVal {}" << std::endl;

    os << "private[isl] val lib = LibraryLoader.create(classOf[ISLLib])" << std::endl;
    os << "  " << ".load(\"isl\")" << std::endl << std::endl;
    os << "private[isl] trait ISLLib:" << std::endl;
    for (const auto& f : functions_by_name) {
        const auto& name = f.first;
        if(name == "isl_id_to_ast_expr_try_get")
            continue;
        if(name == "isl_id_to_id_try_get")
            continue;
        if(name == "isl_ctx_parse_options")
            continue;
        if(name == "isl_mat_left_hermite")
            continue;
        if(name == "isl_args_parse")
            continue;
        if(name.substr(0, 4) != "isl_")
            continue;
        os << "  def " << name;
        printFunctionParameters(os, f.second, false, true);
        os << std::endl << std::endl;
    }

    for (const auto &c : classes) {
        if(c.first.substr(0, 4) != "isl_"){
            std::cerr << "Warning: `" << c.first <<"` in the classes list?" << std::endl;
            continue;
        }
        os << "class " << isl_class_to_scala(c.first) << " private[isl] (private[isl] var p: Pointer) {" << std::endl;
        for (const auto & ms : c.second.methods) {
            for(const auto & m : ms.second) {
                if(c.second.is_static(m))
                    continue;
                auto prefixLessName = m->getNameAsString().substr(c.first.length() + 1);
                auto name = to_snake_case(prefixLessName);
                os << "  def ";
                printId(os, name);
                printMethodParameters(os, m);
                os << " =" << std::endl;
                printLibraryCall(os, m, true);
                os << std::endl;
            }
        }

        std::string print_method_name = "isl_printer_print_" + c.first.substr(4);
        const auto& print_method = functions_by_name.find(print_method_name);
        std::string ctx_getter_name = c.first + "_get_ctx";
        const auto& ctx_getter = functions_by_name.find(ctx_getter_name);
        if(c.second.fn_to_str != nullptr) {
            os << "  override def toString : String =" << std::endl;
            os << "    lib." << c.second.fn_to_str->getNameAsString() << "(this)" << std::endl;
        } else if(print_method != functions_by_name.end() && ctx_getter != functions_by_name.end()) {
            os << "  override def toString : String =" << std::endl;
            os << "    val ctx = " << "lib." << ctx_getter->first << "(this)" << std::endl;
            os << "    val p = Printer(using ctx)()" << std::endl;
            os << "    p.print" << isl_class_to_scala(c.first) << "(this)" << std::endl;
            os << "    p.getStr()" << std::endl;
        }
        if(c.second.fn_copy != nullptr) {
            os << "  def copy() =" << std::endl;
            os << "    lib." << c.second.fn_copy->getNameAsString() << "(this)" << std::endl;
        }
        os << "}" << std::endl << std::endl;
        if(c.second.superclass_name != "") {
            auto sc = c.second.superclass_name;
            while(sc != "") {
                os << "given Conversion[" << isl_class_to_scala(c.first) << ", " << isl_class_to_scala(sc) << "] with" << std::endl;
                os << "  def apply(that: " << isl_class_to_scala(c.first) << "): " << isl_class_to_scala(sc) << " =" << std::endl;
                os << "    new " << isl_class_to_scala(sc) << "(that.p)" << std::endl << std::endl;
                sc = classes[sc].superclass_name;
            }
        }
        
        os << "object " << isl_class_to_scala(c.first) << ":" << std::endl;
        os << "  private[isl] given Conversion[" << isl_class_to_scala(c.first) << ", Pointer] = _.p" << std::endl;
        os << "  private[isl] given Conversion[Pointer, " << isl_class_to_scala(c.first) << "] = new " << isl_class_to_scala(c.first) << "(_)" << std::endl;
        for (const auto & cc : c.second.constructors) {
            auto prefixLessName = cc->getNameAsString().substr(c.first.length() + 1);
            os << "  @targetName(\"" << cc->getNameAsString() << "\")" << std::endl;
            os << "  inline def apply";
            printFunctionParameters(os, cc, true);
            os << " =" << std::endl;
            printLibraryCall(os, cc, false);
            os << std::endl;
        }
        for (const auto & ms : c.second.methods) {
            for(const auto & m : ms.second) {
                if(!c.second.is_static(m))
                    continue;
                if(m->getNameAsString() == "isl_args_parse")
                    continue;
                auto prefixLessName = m->getNameAsString().substr(c.first.length() + 1);
                auto name = to_snake_case(prefixLessName);
                os << "  inline def ";
                printId(os, name);
                printFunctionParameters(os, m, true);
                os << " =" << std::endl;
                printLibraryCall(os, m, false);
                os << std::endl;
            }
        }
        os << std::endl;
    }
    
    for(auto const & c : callback_types) {
        os << "trait " << c.first << ":"<< std::endl;
        os << "  @Delegate" << std::endl;
        os << "  def apply";
        unsigned i = 0;
        os << "(";

        os << "arg" << ++i << ": " << isl_type_to_scala(c.second->param_types().front(), true);
        for(auto const & p : c.second->param_types().drop_front()) {
            os << ", ";
            os << "arg" << ++i << ": " << isl_type_to_scala(p, true);
        }
        os << ")";
        os << ": " << isl_type_to_scala(c.second->getReturnType(), true) << std::endl;
    }

    os.flush();
}