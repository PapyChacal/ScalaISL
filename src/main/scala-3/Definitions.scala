package com.github.papychacal.isl

private[isl] given Conversion[DimType, Int] = _.ordinal
private[isl] given Conversion[Int, DimType] = DimType.fromOrdinal

enum DimType:
  case Cst
  case Param
  case In
  case Out
  case Div
  case All

