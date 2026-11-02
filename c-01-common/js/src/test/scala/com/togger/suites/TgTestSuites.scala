package com.togger.suites

import cats.effect.testing.scalatest.AsyncIOSpec
import com.togger.logging.TestLoggingJs
import com.togger.suites.TestNameHelper
import org.scalamock.scalatest.AsyncMockFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.wordspec.AsyncWordSpec

class TgAsyncWordSpecJs extends AsyncWordSpec with AsyncIOSpec with Matchers with TestLoggingJs with TestNameHelper

class TgAsyncWordSpecMockJs
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with TestLoggingJs
    with AsyncMockFactory
    with TestNameHelper

class TgWordSpecJs extends AnyWordSpec with Matchers with TestLoggingJs with TestNameHelper

class TgWordSpecMockJs extends AnyWordSpec with Matchers with TestLoggingJs with MockFactory with TestNameHelper
