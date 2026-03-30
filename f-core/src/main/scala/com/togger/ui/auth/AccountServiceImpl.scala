package com.togger.ui.auth

import com.togger.auth.AuthAccountFe
import com.togger.logging.TgLogging

class AccountServiceImpl(loginRepo: LoginRepo) extends AccountService with TgLogging {

  override def get: Option[AuthAccountFe] =
    loginRepo.account

}
