package com.togger.ui.auth

import cats.data.EitherT
import cats.effect.IO
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.UnprotectedBackendClient
import com.togger.common.TgEndpoints.TgBaseEndpoint
import com.togger.common.TgJsonFailure
import com.togger.dto.login.LoginDto
import com.togger.login.AuthEndpoints
import sttp.client3.UriContext

trait LoginService {
  def login(dto: LoginDto): IO[Either[TgJsonFailure, Unit]]
}

class LoginServiceImpl(baseEndpoint: TgBaseEndpoint, securedEndpoint: SecuredEndpoint, loginRepo: LoginRepo)
    extends LoginService {
  val authEndpoints = new AuthEndpoints(baseEndpoint, securedEndpoint)

  val unprotectedBackendClient = new UnprotectedBackendClient(baseUri = uri"http://localhost:8081/")

  override def login(dto: LoginDto): IO[Either[TgJsonFailure, Unit]] =
    EitherT(unprotectedBackendClient.httpCall(authEndpoints.loginEndpoint)(dto))
      .map(success => loginRepo.save(success.body.get))
      .value
}
