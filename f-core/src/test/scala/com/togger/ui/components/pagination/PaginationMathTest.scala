package com.togger.ui.components.pagination

import com.togger.common.PaginatedResponse
import com.togger.common.suites.AsyncJsWordSpec

class PaginationMathTest extends AsyncJsWordSpec {

  import PageItem.{Ellipsis => E, Page => P}

  private def pages(current: Int, total: Int) = PaginationMath.pages(current, total)

  testNameFromClassName should {

    "show nothing for zero pages" in { _ => pages(1, 0) shouldBe Seq.empty }

    "show a single page" in { _ => pages(1, 1) shouldBe Seq(P(1)) }

    "show all pages for 7 pages" in { _ => pages(4, 7) shouldBe (1 to 7).map(P(_)) }

    "collapse at 8 pages with current at the start" in { _ => pages(1, 8) shouldBe Seq(P(1), P(2), E, P(8)) }

    "fill a single-page gap instead of an ellipsis" in { _ =>
      pages(4, 8) shouldBe Seq(P(1), P(2), P(3), P(4), P(5), E, P(8))
    }

    "collapse at 100 pages with current in the middle" in { _ =>
      pages(50, 100) shouldBe Seq(P(1), E, P(49), P(50), P(51), E, P(100))
    }

    "collapse at 100 pages with current at the end" in { _ => pages(100, 100) shouldBe Seq(P(1), E, P(99), P(100)) }

    "clamp a current page beyond the total" in { _ => pages(500, 100) shouldBe pages(100, 100) }

    "clamp a current page below one" in { _ => pages(-3, 100) shouldBe pages(1, 100) }
  }
}

class PaginationStateTest extends AsyncJsWordSpec {

  testNameFromClassName should {

    "compute first and last item on a full page" in { _ =>
      val state = PaginationState.fromResponse(PaginatedResponse.of(2, 20, List.fill(20)(1), 132L))
      state shouldBe PaginationState(page = 2, size = 20, totalItems = 132L, totalPages = 7, itemsReturned = 20)
      (state.firstItem, state.lastItem) shouldBe ((21, 40))
    }

    "compute the last item on a partial last page" in { _ =>
      val state = PaginationState.fromResponse(PaginatedResponse.of(7, 20, List.fill(12)(1), 132L))
      (state.firstItem, state.lastItem) shouldBe ((121, 132))
    }

    "report zero for an empty result" in { _ =>
      val state = PaginationState.fromResponse(PaginatedResponse.empty[Int](1, 20))
      (state.firstItem, state.lastItem) shouldBe ((0, 0))
    }
  }
}
