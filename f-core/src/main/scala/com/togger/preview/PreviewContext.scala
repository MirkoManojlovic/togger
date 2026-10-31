package com.togger.preview

import com.togger.common.TgClock
import com.togger.ui.auth.LoginRepo

/** Everything a `*Preview` service needs: the storage backed collections, the session and the clock. */
class PreviewContext(val db: PreviewDb, val loginRepo: LoginRepo, val clock: TgClock)
