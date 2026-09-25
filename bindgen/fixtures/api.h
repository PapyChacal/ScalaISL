#ifndef BINDGEN_FIXTURE_API_H
#define BINDGEN_FIXTURE_API_H

typedef struct isl_fixture isl_fixture;

enum isl_fixture_mode {
	isl_fixture_mode_first = 0,
	isl_fixture_mode_second = 2
};

/** A callback invoked for every fixture value. */
typedef int (*isl_fixture_callback)(__isl_keep isl_fixture *value, void *user);

/** Construct a fixture from a textual description. */
__isl_constructor __isl_give isl_fixture *isl_fixture_read_from_str(
	__isl_keep void *ctx, const char *text);

/** Visit each value without transferring ownership. */
__isl_export int isl_fixture_foreach(__isl_keep isl_fixture *fixture,
	isl_fixture_callback callback, void *user);

/** Install a callback that remains reachable from the returned object. */
__isl_export __isl_give isl_fixture *isl_ast_build_set_fixture(
	__isl_take isl_fixture *fixture, isl_fixture_callback callback, void *user);

__isl_export __isl_give isl_fixture *isl_fixture_coalesce(__isl_take isl_fixture *fixture);

__isl_export __isl_give isl_fixture *isl_fixture_copy(__isl_keep isl_fixture *fixture);
__isl_export void isl_fixture_free(__isl_take isl_fixture *fixture);

#endif
