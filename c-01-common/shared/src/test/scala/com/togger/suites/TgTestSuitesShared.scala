package com.togger.suites

import cats.effect.testing.scalatest.AsyncIOSpec
import com.togger.logging.TestLoggingShared
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.{AnyWordSpec, AsyncWordSpec}

class TgAsyncWordSpecShared
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with TestLoggingShared
    with TestNameHelper

class TgWordSpecShared extends AnyWordSpec with Matchers with TestLoggingShared with TestNameHelper

class TgWordSpecMockShared extends AnyWordSpec with Matchers with TestLoggingShared with MockFactory with TestNameHelper
