# Build the ScalaISL extractor against ISL's private, version-pinned
# interface generator. ISL_INTERFACE_BUILD must already have been configured.

ifndef ISL_INTERFACE_SOURCE
$(error ISL_INTERFACE_SOURCE must point to ISL's interface source directory)
endif
ifndef ISL_INTERFACE_BUILD
$(error ISL_INTERFACE_BUILD must point to ISL's configured interface build directory)
endif

include $(ISL_INTERFACE_BUILD)/Makefile

SCALA_EXTRACTOR_SOURCE ?= $(CURDIR)
SCALA_EXTRACTOR_OUTPUT ?= $(CURDIR)/extract_interface_scala
SCALA_EXTRACTOR_OBJECTS = scala-generator.o scala-backend.o scala-driver.o
SCALA_EXTRACTOR_INCLUDES = \
	-I$(ISL_INTERFACE_BUILD) \
	-I$(ISL_INTERFACE_SOURCE) \
	-I$(ISL_INTERFACE_BUILD)/include \
	-I$(ISL_INTERFACE_SOURCE)/include \
	-I$(SCALA_EXTRACTOR_SOURCE)
SCALA_EXTRACTOR_COMPILE = $(CXX) -DHAVE_CONFIG_H $(SCALA_EXTRACTOR_INCLUDES) \
	$(CLANG_CXXFLAGS) $(CXXFLAGS)

.PHONY: scala-extractor
scala-extractor: $(SCALA_EXTRACTOR_OUTPUT)

$(SCALA_EXTRACTOR_OUTPUT): $(SCALA_EXTRACTOR_OBJECTS)
	$(SHELL) $(ISL_INTERFACE_BUILD)/libtool --tag=CXX --mode=link \
		$(CXX) $(CXXFLAGS) $(CLANG_CXXFLAGS) \
		$(CLANG_LDFLAGS) $(CLANG_RFLAG) \
		-o $@ $^ $(CLANG_LIBS) $(CLANG_LDFLAGS)

scala-generator.o: $(ISL_INTERFACE_SOURCE)/generator.cc $(ISL_INTERFACE_SOURCE)/generator.h
	$(SCALA_EXTRACTOR_COMPILE) -c -o $@ $<

scala-backend.o: $(SCALA_EXTRACTOR_SOURCE)/scala.cc $(SCALA_EXTRACTOR_SOURCE)/scala.h
	$(SCALA_EXTRACTOR_COMPILE) -c -o $@ $<

scala-driver.o: $(SCALA_EXTRACTOR_SOURCE)/extract_interface_scala.cc \
		$(SCALA_EXTRACTOR_SOURCE)/scala.h
	$(SCALA_EXTRACTOR_COMPILE) -c -o $@ $<
