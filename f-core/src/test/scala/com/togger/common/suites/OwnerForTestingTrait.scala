package com.togger.common.suites

import com.raquo.laminar.api.L._


trait OwnerForTestingTrait {
  def ownerForTesting: OwnerForTesting =
    new OwnerForTesting()
}


class OwnerForTesting extends Owner {

//  def _testSubscriptions: List[Subscription] = subscriptions

  override def killSubscriptions(): Unit = {
    super.killSubscriptions()
  }
}