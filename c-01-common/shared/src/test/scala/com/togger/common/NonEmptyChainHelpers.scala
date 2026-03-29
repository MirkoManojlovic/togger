package com.togger.common

import cats.data.NonEmptyChain

trait NonEmptyChainHelpers {
  implicit class NonEmptyChainOps[A](nec: NonEmptyChain[A]) {
    def asList: List[A] = nec.toNonEmptyList.toList
  }
}
