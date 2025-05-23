if HAVE_CLANG
if HAVE_CXX11
MAYBE_INTERFACE = interface
FORCE:
interface/extract_interface$(BUILD_EXEEXT): FORCE
	$(MAKE) $(AM_MAKEFLAGS) -C interface extract_interface$(BUILD_EXEEXT)
endif
endif
SUBDIRS = . $(MAYBE_INTERFACE) doc
DIST_SUBDIRS = $(MAYBE_INTERFACE) doc

ACLOCAL_AMFLAGS = -I m4
AUTOMAKE_OPTIONS = nostdinc subdir-objects

lib_LTLIBRARIES = libisl.la
noinst_PROGRAMS = isl_test isl_polyhedron_sample isl_pip \
	isl_polyhedron_minimize isl_polytope_scan \
	isl_polyhedron_detect_equalities \
	isl_polyhedron_remove_redundant_equalities isl_cat \
	isl_closure isl_bound isl_schedule isl_codegen isl_test_int \
	isl_flow isl_flow_cmp isl_schedule_cmp
TESTS = isl_test codegen_test.sh pip_test.sh bound_test.sh isl_test_int \
	flow_test.sh schedule_test.sh
CPP_H = $(srcdir)/include/isl/cpp.h $(srcdir)/include/isl/typed_cpp.h
if HAVE_CXX11
  noinst_PROGRAMS += isl_test2 isl_test_cpp
  TESTS += isl_test2 isl_test_cpp isl_test_cpp_failed.sh
endif
if HAVE_CXX17
  noinst_PROGRAMS += isl_test_cpp17 isl_test_cpp17-checked
  TESTS += isl_test_cpp17 isl_test_cpp17-checked
endif
if HAVE_CXX11
  noinst_PROGRAMS += isl_test_cpp-checked isl_test_cpp-checked-conversion
  TESTS += isl_test_cpp-checked isl_test_cpp-checked-conversion
endif
if HAVE_PYTHON
  TESTS += isl_test_python.py
  noinst_PYTHON = interface/isl.py
  isl_test_python.py: interface/isl.py libisl.la
endif
TEST_EXTENSIONS = .py
AM_TESTS_ENVIRONMENT = \
	export PYTHONPATH=interface; \
	export ISL_DYLD_LIBRARY_PATH=.libs; \
	export LD_LIBRARY_PATH=".libs:$(LD_LIBRARY_PATH)";
PY_LOG_COMPILER = $(PYTHON)

if IMATH_FOR_MP

MP_SRC = \
	isl_imath.c \
	isl_imath.h \
	isl_int_imath.h \
	imath_wrap/gmp_compat.h \
	imath_wrap/imath.h \
	imath_wrap/imrat.h \
	imath_wrap/wrap.h \
	imath_wrap/gmp_compat.c \
	imath_wrap/imath.c \
	imath_wrap/imrat.c

noinst_PROGRAMS += isl_test_imath
TESTS += isl_test_imath

if SMALL_INT_OPT
MP_SRC += isl_int_sioimath.h \
	isl_int_sioimath.c \
	isl_val_sioimath.c
else
MP_SRC += isl_val_imath.c
endif

MP_INCLUDE_H =
endif

if GMP_FOR_MP
if NEED_GET_MEMORY_FUNCTIONS
GET_MEMORY_FUNCTIONS=mp_get_memory_functions.c
endif

MP_SRC = \
	$(GET_MEMORY_FUNCTIONS) \
	isl_int_gmp.h \
	isl_gmp.c \
	isl_val_gmp.c

MP_INCLUDE_H = include/isl/val_gmp.h
endif

includes = -I. -I$(srcdir) -I$(srcdir)/include -Iinclude/
AM_CPPFLAGS = $(includes) @MP_CPPFLAGS@
AM_CFLAGS = @WARNING_FLAGS@ @MP_CFLAGS@

libisl_la_SOURCES = \
	$(MP_SRC) \
	isl_aff.c \
	isl_aff_map.c \
	isl_aff_private.h \
	isl_affine_hull.c \
	isl_arg.c \
	isl_ast.c \
	isl_ast_private.h \
	isl_ast_build.c \
	isl_ast_build_private.h \
	isl_ast_build_expr.c \
	isl_ast_build_expr.h \
	isl_ast_codegen.c \
	isl_ast_graft.c \
	isl_ast_graft_private.h \
	isl_basis_reduction.h \
	basis_reduction_tab.c \
	isl_bernstein.c \
	isl_bernstein.h \
	isl_blk.c \
	isl_blk.h \
	isl_bound.c \
	isl_bound.h \
	isl_box.c \
	isl_coalesce.c \
	isl_constraint.c \
	isl_constraint_private.h \
	isl_convex_hull.c \
	isl_ctx.c \
	isl_ctx_private.h \
	isl_deprecated.c \
	isl_dim_map.h \
	isl_dim_map.c \
	isl_equalities.c \
	isl_equalities.h \
	isl_factorization.c \
	isl_factorization.h \
	isl_farkas.c \
	isl_ffs.c \
	isl_flow.c \
	isl_fold.c \
	isl_hash.c \
	isl_hash_private.h \
	isl_id_to_ast_expr.c \
	isl_id_to_id.c \
	isl_id_to_pw_aff.c \
	isl_ilp.c \
	isl_ilp_private.h \
	isl_input.c \
	isl_int.h \
	isl_list_private.h \
	isl_local_private.h \
	isl_local.h \
	isl_local.c \
	isl_local_space_private.h \
	isl_local_space.c \
	isl_lp.c \
	isl_lp_private.h \
	isl_map.c \
	isl_map_list.c \
	isl_map_simplify.c \
	isl_map_subtract.c \
	isl_map_private.h \
	isl_map_to_basic_set.c \
	isl_mat.c \
	isl_mat_private.h \
	isl_maybe_aff.h \
	isl_morph.c \
	isl_morph.h \
	isl_id.c \
	isl_id_private.h \
	isl_obj.c \
	isl_options.c \
	isl_options_private.h \
	isl_output.c \
	isl_output_private.h \
	isl_point_private.h \
	isl_point.c \
	isl_polynomial_private.h \
	isl_polynomial.c \
	isl_printer_private.h \
	isl_printer.c \
	print.c \
	isl_range.c \
	isl_range.h \
	isl_reordering.c \
	isl_reordering.h \
	isl_sample.h \
	isl_sample.c \
	isl_scan.c \
	isl_scan.h \
	isl_schedule.c \
	isl_schedule_band.c \
	isl_schedule_band.h \
	isl_schedule_node.c \
	isl_schedule_node_private.h \
	isl_schedule_read.c \
	isl_schedule_tree.c \
	isl_schedule_tree.h \
	isl_schedule_private.h \
	isl_schedule_constraints.c \
	isl_schedule_constraints.h \
	isl_scheduler.c \
	isl_scheduler.h \
	isl_scheduler_clustering.c \
	isl_scheduler_clustering.h \
	isl_scheduler_scc.c \
	isl_scheduler_scc.h \
	isl_set_list.c \
	isl_sort.c \
	isl_sort.h \
	isl_space.c \
	isl_space_private.h \
	isl_stream.c \
	isl_stream_private.h \
	isl_seq.c \
	isl_seq.h \
	isl_set_to_ast_graft_list.c \
	isl_set_to_ast_graft_list.h \
	isl_stride.c \
	isl_tab.c \
	isl_tab.h \
	isl_tab_pip.c \
	isl_tarjan.c \
	isl_tarjan.h \
	isl_transitive_closure.c \
	isl_union_map.c \
	isl_union_map_private.h \
	isl_union_set_private.h \
	isl_val.c \
	isl_val_private.h \
	isl_vec_private.h \
	isl_vec.c \
	isl_version.c \
	isl_vertices_private.h \
	isl_vertices.c \
	isl_yaml.h
libisl_la_LIBADD = @MP_LIBS@
libisl_la_LDFLAGS = -version-info @versioninfo@ \
	@MP_LDFLAGS@

isl_test_LDFLAGS = @MP_LDFLAGS@
isl_test_LDADD = libisl.la @MP_LIBS@

isl_test2_SOURCES = \
	isl_test2.cc \
	$(srcdir)/include/isl/cpp.h
isl_test2_LDFLAGS = @MP_LDFLAGS@
isl_test2_LDADD = libisl.la @MP_LIBS@

isl_test_int_LDFLAGS = @MP_LDFLAGS@
isl_test_int_LDADD = libisl.la @MP_LIBS@

if IMATH_FOR_MP
isl_test_imath_LDFLAGS = @MP_LDFLAGS@
isl_test_imath_LDADD = libisl.la @MP_LIBS@
endif

isl_polyhedron_sample_LDADD = libisl.la
isl_polyhedron_sample_SOURCES = \
	polyhedron_sample.c

isl_pip_LDFLAGS = @MP_LDFLAGS@
isl_pip_LDADD = libisl.la @MP_LIBS@
isl_pip_SOURCES = \
	pip.c

isl_schedule_LDFLAGS = @MP_LDFLAGS@
isl_schedule_LDADD = libisl.la @MP_LIBS@
isl_schedule_SOURCES = \
	schedule.c

isl_schedule_cmp_LDFLAGS = @MP_LDFLAGS@
isl_schedule_cmp_LDADD = libisl.la @MP_LIBS@
isl_schedule_cmp_SOURCES = \
	schedule_cmp.c

isl_flow_LDFLAGS = @MP_LDFLAGS@
isl_flow_LDADD = libisl.la @MP_LIBS@
isl_flow_SOURCES = \
	flow.c

isl_flow_cmp_LDFLAGS = @MP_LDFLAGS@
isl_flow_cmp_LDADD = libisl.la @MP_LIBS@
isl_flow_cmp_SOURCES = \
	flow_cmp.c

isl_codegen_LDFLAGS = @MP_LDFLAGS@
isl_codegen_LDADD = libisl.la @MP_LIBS@
isl_codegen_SOURCES = \
	codegen.c

isl_bound_LDFLAGS = @MP_LDFLAGS@
isl_bound_LDADD = libisl.la @MP_LIBS@
isl_bound_SOURCES = \
	bound.c

isl_polyhedron_minimize_LDFLAGS = @MP_LDFLAGS@
isl_polyhedron_minimize_LDADD = libisl.la @MP_LIBS@
isl_polyhedron_minimize_SOURCES = \
	polyhedron_minimize.c

isl_polytope_scan_LDADD = libisl.la
isl_polytope_scan_SOURCES = \
	polytope_scan.c

isl_polyhedron_detect_equalities_LDADD = libisl.la
isl_polyhedron_detect_equalities_SOURCES = \
	polyhedron_detect_equalities.c

isl_polyhedron_remove_redundant_equalities_LDADD = libisl.la
isl_polyhedron_remove_redundant_equalities_SOURCES = \
	polyhedron_remove_redundant_equalities.c

isl_cat_LDADD = libisl.la
isl_cat_SOURCES = \
	cat.c

isl_closure_LDADD = libisl.la
isl_closure_SOURCES = \
	closure.c

isl_test_cpp_CPPFLAGS = $(AM_CPPFLAGS) -UCOMPILE_ERROR
isl_test_cpp_SOURCES = \
	isl_test_cpp.cc \
	$(srcdir)/include/isl/cpp.h
isl_test_cpp_LDFLAGS = @MP_LDFLAGS@
isl_test_cpp_LDADD = libisl.la @MP_LIBS@

# This program is not meant to be compiled by default.
# In fact it is meant not to be compilable.
# It is identical to isl_test_cpp, except that it gets compiled
# with the COMPILE_ERROR flag set.
EXTRA_PROGRAMS = isl_test_cpp_failed
isl_test_cpp_failed_CPPFLAGS = $(AM_CPPFLAGS) -DCOMPILE_ERROR
isl_test_cpp_failed_SOURCES = \
	isl_test_cpp.cc \
	$(srcdir)/include/isl/cpp.h
isl_test_cpp_failed_LDFLAGS = @MP_LDFLAGS@
isl_test_cpp_failed_LDADD = libisl.la @MP_LIBS@

isl_test_cpp17_SOURCES = \
	isl_test_cpp17.cc \
	$(srcdir)/include/isl/cpp.h
isl_test_cpp17_LDFLAGS = @MP_LDFLAGS@
isl_test_cpp17_LDADD = libisl.la @MP_LIBS@

isl_test_cpp_checked_SOURCES = \
	isl_test_cpp-checked.cc \
	$(srcdir)/include/isl/cpp-checked.h
isl_test_cpp_checked_LDFLAGS = @MP_LDFLAGS@
isl_test_cpp_checked_LDADD = libisl.la @MP_LIBS@

isl_test_cpp17_checked_SOURCES = \
	isl_test_cpp17-checked.cc \
	$(srcdir)/include/isl/cpp.h
isl_test_cpp17_checked_LDFLAGS = @MP_LDFLAGS@
isl_test_cpp17_checked_LDADD = libisl.la @MP_LIBS@

isl_test_cpp_checked_conversion_SOURCES = \
	isl_test_cpp-checked-conversion.cc \
	$(srcdir)/include/isl/cpp-checked-conversion.h
isl_test_cpp_checked_conversion_LDFLAGS = @MP_LDFLAGS@
isl_test_cpp_checked_conversion_LDADD = libisl.la @MP_LIBS@

# dummy library that captures the dependencies on all headers
# that are relevant for the bindings
noinst_LIBRARIES = libdep.a
libdep_a_SOURCES = dep.c

if HAVE_CLANG
if HAVE_CXX11
CP_IF_DIFFERENT = "$(srcdir)/cp_if_different.sh"
.gen/isl.py.core: interface/extract_interface$(BUILD_EXEEXT) libdep.a
	$(AM_V_GEN)$(MKDIR_P) ".gen" && \
	interface/extract_interface$(BUILD_EXEEXT) \
		--language=python $(includes) $(srcdir)/all.h \
		> .gen/isl.py.Tcore && mv .gen/isl.py.Tcore $@
$(srcdir)/interface/isl.py.core: .gen/isl.py.core
	$(AM_V_at)$(CP_IF_DIFFERENT) .gen/isl.py.core $@

.gen/cpp.h: interface/extract_interface$(BUILD_EXEEXT) \
		libdep.a cpp/cpp.h.top cpp/cpp.h.bot
	$(AM_V_GEN)$(MKDIR_P) ".gen" && \
	(cat $(srcdir)/cpp/cpp.h.top $(srcdir)/all.h && \
		interface/extract_interface$(BUILD_EXEEXT) --language=cpp \
			$(includes) $(srcdir)/all.h && \
		cat $(srcdir)/cpp/cpp.h.bot) \
			> .gen/cpp.Th && mv .gen/cpp.Th $@
$(srcdir)/include/isl/cpp.h: .gen/cpp.h
	$(AM_V_at)$(CP_IF_DIFFERENT) .gen/cpp.h $@

.gen/cpp-checked.h: \
		interface/extract_interface$(BUILD_EXEEXT) libdep.a \
		cpp/cpp-checked.h.top cpp/cpp-checked.h.bot
	$(AM_V_GEN)$(MKDIR_P) ".gen" && \
	(cat $(srcdir)/cpp/cpp-checked.h.top $(srcdir)/all.h && \
		interface/extract_interface$(BUILD_EXEEXT) \
			--language=cpp-checked \
			$(includes) $(srcdir)/all.h && \
		cat $(srcdir)/cpp/cpp-checked.h.bot) \
			> .gen/cpp-checked.Th && \
			mv .gen/cpp-checked.Th $@
$(srcdir)/include/isl/cpp-checked.h: .gen/cpp-checked.h
	$(AM_V_at)$(CP_IF_DIFFERENT) .gen/cpp-checked.h $@

.gen/cpp-checked-conversion.h: \
		interface/extract_interface$(BUILD_EXEEXT) \
		libdep.a \
		cpp/cpp-checked-conversion.h.top \
		cpp/cpp-checked-conversion.h.bot
	$(AM_V_GEN)$(MKDIR_P) ".gen" && \
	(cat $(srcdir)/cpp/cpp-checked-conversion.h.top && \
		interface/extract_interface$(BUILD_EXEEXT) \
			--language=cpp-checked-conversion \
			$(includes) $(srcdir)/all.h && \
		cat $(srcdir)/cpp/cpp-checked-conversion.h.bot) \
			> .gen/cpp-checked-conversion.Th && \
			mv .gen/cpp-checked-conversion.Th $@
$(srcdir)/include/isl/cpp-checked-conversion.h: .gen/cpp-checked-conversion.h
	$(AM_V_at)$(CP_IF_DIFFERENT) .gen/cpp-checked-conversion.h $@

.gen/typed_cpp.h: interface/extract_interface$(BUILD_EXEEXT) \
		libdep.a cpp/typed_cpp.h.top cpp/typed_cpp.h.bot
	$(AM_V_GEN)$(MKDIR_P) ".gen" && \
	(cat $(srcdir)/cpp/typed_cpp.h.top && \
		interface/extract_interface$(BUILD_EXEEXT) \
			--language=template-cpp \
			$(includes) $(srcdir)/all.h && \
		cat $(srcdir)/cpp/typed_cpp.h.bot) \
			> .gen/typed_cpp.Th && mv .gen/typed_cpp.Th $@
$(srcdir)/include/isl/typed_cpp.h: .gen/typed_cpp.h
	$(AM_V_at)$(CP_IF_DIFFERENT) .gen/typed_cpp.h $@
endif
endif

interface/isldlname.py: libisl.la
	$(AM_V_GEN)$(MKDIR_P) "interface" && \
		$(GREP) dlname libisl.la | \
		$(SED) -e 's/dlname/isl_dlname/' > $@
interface/isl.py: python/isl.py.top interface/isldlname.py \
		$(srcdir)/interface/isl.py.core
	$(AM_V_GEN)cat interface/isldlname.py $(srcdir)/python/isl.py.top \
		$(srcdir)/interface/isl.py.core > interface/isl.Tpy && \
		mv interface/isl.Tpy $@

nodist_pkginclude_HEADERS = \
	include/isl/stdint.h
pkginclude_HEADERS = \
	$(CPP_H) \
	$(MP_INCLUDE_H) \
	include/isl/aff.h \
	include/isl/aff_type.h \
	include/isl/arg.h \
	include/isl/ast.h \
	include/isl/ast_type.h \
	include/isl/ast_build.h \
	include/isl/constraint.h \
	include/isl/ctx.h \
	include/isl/fixed_box.h \
	include/isl/flow.h \
	include/isl/id.h \
	include/isl/id_type.h \
	include/isl/id_to_ast_expr.h \
	include/isl/id_to_id.h \
	include/isl/id_to_pw_aff.h \
	include/isl/ilp.h \
	include/isl/hash.h \
	include/isl/hmap.h \
	include/isl/hmap_templ.c \
	include/isl/list.h \
	include/isl/local_space.h \
	include/isl/lp.h \
	include/isl/mat.h \
	include/isl/map.h \
	include/isl/map_to_basic_set.h \
	include/isl/map_type.h \
	include/isl/maybe.h \
	include/isl/maybe_ast_expr.h \
	include/isl/maybe_basic_set.h \
	include/isl/maybe_id.h \
	include/isl/maybe_pw_aff.h \
	include/isl/maybe_templ.h \
	include/isl/multi.h \
	include/isl/obj.h \
	include/isl/options.h \
	include/isl/point.h \
	include/isl/polynomial.h \
	include/isl/polynomial_type.h \
	include/isl/printer.h \
	include/isl/printer_type.h \
	include/isl/schedule.h \
	include/isl/schedule_node.h \
	include/isl/schedule_type.h \
	include/isl/set.h \
	include/isl/set_type.h \
	include/isl/space.h \
	include/isl/space_type.h \
	include/isl/stream.h \
	include/isl/stride_info.h \
	include/isl/union_map.h \
	include/isl/union_map_type.h \
	include/isl/union_set.h \
	include/isl/union_set_type.h \
	include/isl/val.h \
	include/isl/val_type.h \
	include/isl/vec.h \
	include/isl/version.h \
	include/isl/vertices.h

CPP_INTERFACES = \
	$(srcdir)/include/isl/cpp.h \
	$(srcdir)/include/isl/cpp-checked.h \
	$(srcdir)/include/isl/cpp-checked-conversion.h \
	$(srcdir)/include/isl/typed_cpp.h
if HAVE_CLANG
if HAVE_CXX11
    BUILT_CPP_INTERFACES = $(CPP_INTERFACES)
endif
endif
BUILT_SOURCES = gitversion.h $(BUILT_CPP_INTERFACES) interface/isl.py
CLEANFILES = \
	gitversion.h \
	interface/isldlname.py \
	interface/isl.py \
	interface/isl.pyc \
	interface/__pycache__/*.pyc

DISTCLEANFILES = \
	.gen/cpp.h \
	.gen/cpp-checked-conversion.h \
	.gen/cpp-checked.h \
	.gen/isl.py.core \
	.gen/typed_cpp.h \
	isl-uninstalled.sh \
	isl-uninstalled.pc \
	isl.pc \
	isl.pc.in \
	include/isl/stdint.h

EXTRA_DIST = \
	LICENSE \
	isl_config_post.h \
	isl_aff_lex_templ.c \
	isl_align_params_templ.c \
	isl_align_params_bin_templ.c \
	isl_ast_node_set_field_templ.c \
	basis_reduction_templ.c \
	isl_bind_domain_templ.c \
	bset_to_bmap.c \
	bset_from_bmap.c \
	isl_check_named_params_templ.c \
	check_parse_fail_test_templ.c \
	check_reparse_templ.c \
	check_reparse_test_templ.c \
	check_single_reference_templ.c \
	check_type_range_templ.c \
	isl_copy_tuple_id_templ.c \
	isl_domain_factor_templ.c \
	isl_drop_unused_params_templ.c \
	extract_key.c \
	isl_from_range_templ.c \
	isl_ilp_opt_fn_val_templ.c \
	isl_ilp_opt_multi_val_templ.c \
	isl_ilp_opt_val_templ.c \
	has_single_reference_templ.c \
	isl_insert_domain_templ.c \
	isl_list_macro.h \
	isl_list_templ.c \
	isl_list_templ.h \
	isl_list_read_templ.c \
	isl_list_read_yaml_templ.c \
	isl_map_bound_templ.c \
	isl_map_lexopt_templ.c \
	isl_maybe_ast_graft_list.h \
	isl_maybe_map.h \
	isl_multi_macro.h \
	isl_multi_bind_templ.c \
	isl_multi_explicit_domain.c \
	isl_multi_pw_aff_explicit_domain.c \
	isl_multi_union_pw_aff_explicit_domain.c \
	isl_multi_no_explicit_domain.c \
	isl_multi_no_domain_templ.c \
	isl_multi_templ.c \
	isl_multi_templ.h \
	isl_multi_add_constant_templ.c \
	isl_multi_align_set.c \
	isl_multi_align_union_set.c \
	isl_multi_apply_templ.c \
	isl_multi_apply_explicit_domain_templ.c \
	isl_multi_apply_no_explicit_domain_templ.c \
	isl_multi_apply_set_explicit_domain_templ.c \
	isl_multi_apply_set_no_explicit_domain_templ.c \
	isl_multi_apply_union_set_explicit_domain_templ.c \
	isl_multi_arith_templ.c \
	isl_multi_bin_val_templ.c \
	isl_multi_bind_domain_templ.c \
	isl_multi_check_domain_templ.c \
	isl_multi_cmp.c \
	isl_multi_coalesce.c \
	isl_multi_dim_id_templ.c \
	isl_multi_dims.c \
	isl_multi_domain_templ.c \
	isl_multi_domain_reverse_templ.c \
	isl_multi_floor.c \
	isl_multi_from_base_templ.c \
	isl_multi_from_tuple_templ.c \
	isl_multi_gist.c \
	isl_multi_hash.c \
	isl_multi_insert_domain_templ.c \
	isl_multi_intersect.c \
	isl_multi_identity_templ.c \
	isl_multi_locals_templ.c \
	isl_multi_min_max_templ.c \
	isl_multi_move_dims_templ.c \
	isl_multi_nan_templ.c \
	isl_multi_param_templ.c \
	isl_multi_product_templ.c \
	isl_multi_pw_aff_pullback_templ.c \
	isl_multi_read_no_explicit_domain_templ.c \
	isl_multi_splice_templ.c \
	isl_multi_tuple_id_templ.c \
	isl_multi_un_op_templ.c \
	isl_multi_unbind_params_templ.c \
	isl_multi_union_add_templ.c \
	isl_multi_zero_templ.c \
	isl_multi_zero_space_templ.c \
	isl_opt_mpa_templ.c \
	opt_type.h \
	print_templ.c \
	print_templ_yaml.c \
	print_yaml_field_templ.c \
	isl_power_templ.c \
	isl_project_out_all_params_templ.c \
	isl_project_out_param_templ.c \
	isl_pw_macro.h \
	isl_pw_templ.c \
	isl_pw_templ.h \
	isl_pw_add_constant_templ.c \
	isl_pw_add_constant_multi_val_templ.c \
	isl_pw_add_constant_val_templ.c \
	isl_pw_add_disjoint_templ.c \
	isl_pw_bind_domain_templ.c \
	isl_pw_domain_reverse_templ.c \
	isl_pw_eval.c \
	isl_pw_fix_templ.c \
	isl_pw_from_range_templ.c \
	isl_pw_hash.c \
	isl_pw_insert_dims_templ.c \
	isl_pw_insert_domain_templ.c \
	isl_pw_lift_templ.c \
	isl_pw_locals_templ.c \
	isl_pw_morph_templ.c \
	isl_pw_move_dims_templ.c \
	isl_pw_neg_templ.c \
	isl_pw_opt_templ.c \
	isl_pw_print_templ.c \
	isl_pw_pullback_templ.c \
	isl_pw_range_tuple_id_templ.c \
	isl_pw_scale_templ.c \
	isl_pw_split_dims_templ.c \
	isl_pw_sub_templ.c \
	isl_pw_un_op_templ.c \
	isl_pw_union_opt.c \
	isl_read_from_str_templ.c \
	read_in_string_templ.c \
	set_to_map.c \
	set_from_map.c \
	set_list_from_map_list_inl.c \
	isl_stream_read_pw_with_params_templ.c \
	isl_stream_read_with_params_templ.c \
	isl_tab_lexopt_templ.c \
	isl_test_list_templ.c \
	isl_test_plain_equal_templ.c \
	isl_type_check_equal_space_templ.c \
	isl_type_check_match_range_multi_val.c \
	isl_type_has_equal_space_bin_templ.c \
	isl_type_has_equal_space_templ.c \
	isl_type_has_space_templ.c \
	isl_unbind_params_templ.c \
	uset_to_umap.c \
	uset_from_umap.c \
	isl_union_macro.h \
	isl_union_templ.c \
	isl_union_single.c \
	isl_union_multi.c \
	isl_union_domain_reverse_templ.c \
	isl_union_eval.c \
	isl_union_locals_templ.c \
	isl_union_map_lex_templ.c \
	isl_union_neg.c \
	isl_union_print_templ.c \
	isl_union_pw_templ.c \
	isl_union_sub_templ.c \
	libisl-gdb.py \
	doc/CodingStyle \
	doc/SubmittingPatches \
	doc/implementation.tex \
	doc/isl.bib \
	doc/mypod2latex \
	doc/manual.tex \
	doc/reading.tex \
	doc/user.pod \
	imath/gmp_compat.c \
	imath/gmp_compat.h \
	imath/imath.c \
	imath/imath.h \
	imath/imrat.c \
	imath/imrat.h \
	interface/isl.py.core \
	all.h \
	cpp \
	python \
	isl_test_cpp-generic.cc \
	isl_test_cpp_failed.sh \
	isl_test_cpp17-generic.cc \
	isl_test_python.py \
	cp_if_different.sh \
	test_inputs

dist-hook:
	echo @GIT_HEAD_VERSION@ > $(distdir)/GIT_HEAD_ID
	(cd doc; make manual.pdf)
	cp doc/manual.pdf $(distdir)/doc/
	chmod -R u+w $(distdir)/include/isl
	cp $(CPP_INTERFACES) $(distdir)/include/isl

pkgconfigdir=$(pkgconfig_libdir)
pkgconfig_DATA = $(pkgconfig_libfile)

gitversion.h: @GIT_HEAD@
	$(AM_V_GEN)echo '#define GIT_HEAD_ID "'@GIT_HEAD_VERSION@'"' > $@

install-data-local: $(srcdir)/libisl-gdb.py
	@libisl=`sed -ne "/^library_names=/{s/.*='//;s/'$$//;s/ .*//;p;}" \
		 $(builddir)/libisl.la`; \
	case $$libisl in \
	'') echo Cannot find isl library name. GDB bindings not installed.;; \
	*) echo $(INSTALL_DATA) $(srcdir)/libisl-gdb.py \
		$(DESTDIR)$(libdir)/$$libisl-gdb.py; \
	test -z "$(libdir)" || $(MKDIR_P) "$(DESTDIR)$(libdir)"; \
	$(INSTALL_DATA) $(srcdir)/libisl-gdb.py \
	    $(DESTDIR)$(libdir)/$$libisl-gdb.py; \
	esac

uninstall-local:
	@libisl=`sed -ne "/^library_names=/{s/.*='//;s/'$$//;s/ .*//;p;}" \
		 $(builddir)/libisl.la`; \
	if test -n "$${libisl}"; then \
		rm -f $(DESTDIR)$(libdir)/$$libisl-gdb.py; \
	fi
