package com.togger.ui.auth

import com.togger.auth.AuthAccountFe

trait AccountService {

  def get: Option[AuthAccountFe]

}
