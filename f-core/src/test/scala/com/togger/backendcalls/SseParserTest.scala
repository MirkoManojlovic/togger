package com.togger.backendcalls

import com.togger.common.suites.AsyncJsWordSpec

class SseParserTest extends AsyncJsWordSpec {

  testNameFromClassName should {

    "return the events that are complete and keep the unfinished one" in { _ =>
      SseParser.feed("", "event: chunk\ndata: \"a\"\n\nevent: chunk\ndata: \"b") shouldBe
        ("event: chunk\ndata: \"b", List(SseEvent("chunk", "\"a\"")))
    }

    "finish an event that arrived in two pieces" in { _ =>
      val (pending, _) = SseParser.feed("", "event: done\nda")
      SseParser.feed(pending, "ta: \n\n") shouldBe ("", List(SseEvent("done", "")))
    }

    "read crlf line ends and join multi line data" in { _ =>
      SseParser.feed("", "event: error\r\ndata: one\r\ndata: two\r\n\r\n") shouldBe
        ("", List(SseEvent("error", "one\ntwo")))
    }

    "ignore comments and name events without a type message" in { _ =>
      SseParser.feed("", ": keep-alive\n\ndata: x\n\n") shouldBe ("", List(SseEvent("message", "x")))
    }
  }
}
