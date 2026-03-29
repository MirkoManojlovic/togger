package com.togger.suites

import cats.effect.testing.scalatest.AsyncIOSpec
import com.togger.suites.TestNameHelper
import com.togger.logging.TestLoggingJs
import org.scalamock.scalatest.{AsyncMockFactory, MockFactory}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.{AnyWordSpec, AsyncWordSpec}

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
