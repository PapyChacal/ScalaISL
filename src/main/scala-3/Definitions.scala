package com.github.papychacal.isl

private[isl] given Conversion[Dim, Int] = _.ordinal
private[isl] given Conversion[Int, Dim] = Dim.fromOrdinal

enum Dim:
  case Cst
  case Param
  case In
  case Out
  case Div
  case All

