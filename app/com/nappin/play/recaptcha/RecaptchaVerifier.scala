/*
 * Copyright 2014 Chris Nappin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nappin.play.recaptcha

import play.api.Play.current
import play.api.Logger
import play.api.mvc.{AnyContent, Request}
import play.api.data.Form
import play.api.libs.ws.{WS, WSRequestHolder}

import scala.concurrent.{ExecutionContext, Future}

object RecaptchaVerifier {
		/** The artificial form field key used for captcha errors. */
		val formErrorKey = "com.nappin.play.recaptcha.error"

		/** The recaptcha challenge field name. */
		val recaptchaChallengeField = "recaptcha_challenge_field"

		/** The recaptcha (v1) response field name. */
		val recaptchaResponseField = "recaptcha_response_field"

		/** The recaptcha (v2) response field name. */
		val recaptchaV2ResponseField = "g-recaptcha-response"
}

/**
	* Verifies whether a recaptcha response is valid, by invoking the Google Recaptcha verify web service.
	*
	* Follows the API documented at <a href="https://developers.google.com/recaptcha/docs/verify">Verify Without Plugins</a>.
	*
	* The package private constructor allows injection of dependencies (e.g. for unit testing), the no-arguments public
	* constructor is for production use.
	*
	* @author Chris Nappin
	*/
class RecaptchaVerifier private[recaptcha](parser: ResponseParser, wsRequest: WSRequestHolder) {

		val logger = Logger(this.getClass())

		/**
			* Constructor for production use.
			*/
		def this() = this(new ResponseParser(), WS.url(RecaptchaUrls.getVerifyUrl))

		/**
			* High level API (using Play forms).
			*
			* Binds form data from the request, and if valid then verifies the recaptcha response, by
			* invoking the Google Recaptcha verify web service (API v1 or v2) in a reactive manner.
			* Possible errors include:
			* <ul>
			* <li>Binding error (form validation, regardless of recaptcha)</li>
			* <li>Recaptcha response missing (end user didn't enter it)</li>
			* <li>Recpatcha response incorrect</li>
			* <li>Error invoking the recaptcha service</li>
			* </ul>
			*
			* Apart from generic binding error, the recaptcha errors are populated against the artificial
			* form field key <code>formErrorKey</code>, handled by the recaptcha view template tags.
			*
			* @param form    The form
			* @param request Implicit - The current web request
			* @param context Implicit - The execution context used for futures
			* @return A future that will be the form to use, either populated with an error or success
			* @throws IllegalStateException Developer errors that shouldn't happen - Plugin not
			*                               present or not enabled, no recaptcha challenge, or muliple challenges or responses found
			*/
		def bindFromRequestAndVerify[T](form: Form[T])(implicit request: Request[AnyContent],
				context: ExecutionContext): Future[Form[T]] = {
				checkPluginEnabled()

				val boundForm = form.bindFromRequest

				val challenge = if (RecaptchaPlugin.isApiVersion1)
						readChallenge(request.body.asFormUrlEncoded.get) else ""

				val response = readResponse(request.body.asFormUrlEncoded.get)

				if (response.size < 1) {
						// probably an end user error
						logger.debug("User did not enter a captcha response in the form POST submitted")

						// return the missing required field, plus any other form bind errors that might
						// have happened
						return Future {
								boundForm.withError(
										RecaptchaVerifier.formErrorKey, RecaptchaErrorCode.responseMissing)
						}
				}

				boundForm.fold(
						// form binding failed, so don't call recaptcha
						error => Future {
								logger.debug("Binding error")
								error
						},

						// form binding succeeded, so verify the captcha response
						success => {
								//compare the response with the selenium bypass code, if they match, then bypass the recaptcha service call
								val result = if (response.equals(WidgetHelper.getSeleniumBypassCode)) {
										Future(Right(Success()))
								} else {
										if (RecaptchaPlugin.isApiVersion1)
												verifyV1(challenge, response, request.remoteAddress)
										else verifyV2(response, request.remoteAddress)
								}


								result.map { r => {
										r.fold(
												// captcha incorrect, or a technical error
												error => boundForm.withError(RecaptchaVerifier.formErrorKey, error.code),
												// all success
												success => boundForm
										)
								}
								}
						})
		}

		/**
			* Read the challenge field from the POST'ed response.
			*
			* @param params The POST parameters
			* @return The challenge
			* @throws IllegalStateException If challenge missing, multiple or empty
			*/
		private def readChallenge(params: Map[String, Seq[String]]): String = {
				if (!params.contains(RecaptchaVerifier.recaptchaChallengeField)) {
						// probably a developer error
						val message = "No recaptcha challenge POSTed, check the form submitted was valid"
						logger.error(message)
						throw new IllegalStateException(message)

				} else if (params(RecaptchaVerifier.recaptchaChallengeField).size > 1) {
						// probably a developer error
						val message = "Multiple recaptcha challenge POSTed, check the form submitted was valid"
						logger.error(message)
						throw new IllegalStateException(message)

				} else if (params(RecaptchaVerifier.recaptchaChallengeField)(0).size < 1) {
						// probably a developer error
						val message = "Recaptcha challenge was empty, check the form submitted was valid"
						logger.error(message)
						throw new IllegalStateException(message)
				}
				params(RecaptchaVerifier.recaptchaChallengeField)(0)
		}

		/**
			* Read the response field from the POST'ed response.
			*
			* @param params The POST parameters
			* @return The response
			* @throws IllegalStateException If response missing or multiple
			*/
		private def readResponse(params: Map[String, Seq[String]]): String = {
				val fieldName =
						if (RecaptchaPlugin.isApiVersion1) RecaptchaVerifier.recaptchaResponseField
						else RecaptchaVerifier.recaptchaV2ResponseField

				if (!params.contains(fieldName)) {
						// probably a developer error
						val message = "No recaptcha response POSTed, check the form submitted was valid"
						logger.error(message)
						throw new IllegalStateException(message)

				} else if (params(fieldName).size > 1) {
						// probably a developer error
						val message = "Multiple recaptcha responses POSTed, check the form submitted was valid"
						logger.error(message)
						throw new IllegalStateException(message)
				}
				params(fieldName)(0)
		}

		/**
			* Low level API (independent of Play form and request APIs).
			*
			* Verifies whether a recaptcha response is valid, by invoking the Google Recaptcha API version
			* 1 verify web service in a reactive manner.
			*
			* @param challenge The recaptcha challenge
			* @param response  The recaptcha response, to verify
			* @param remoteIp  The IP address of the end user
			* @param context   Implicit - The execution context used for futures
			* @return A future that will be either an Error (with a code) or Success
			* @throws IllegalStateException Developer errors that shouldn't happen - Plugin not present
			*                               or not enabled
			*/
		def verifyV1(challenge: String, response: String, remoteIp: String)(
				implicit context: ExecutionContext): Future[Either[Error, Success]] = {

				import scala.concurrent.duration._

				checkPluginEnabled()

				// create the v1 POST payload
				val payload = Map(
						"privatekey" -> Seq(
								current.configuration.getString(RecaptchaConfiguration.privateKey).get),
						"remoteip" -> Seq(remoteIp),
						"challenge" -> Seq(challenge),
						"response" -> Seq(response)
				)

				val requestTimeout = current.configuration.getMilliseconds(
						RecaptchaConfiguration.requestTimeout).getOrElse(10.seconds.toMillis)

				logger.info(s"Verifying v1 recaptcha ($response) for $remoteIp")
				val futureResponse = wsRequest.withRequestTimeout(requestTimeout.toInt).post(payload)

				futureResponse.map { response => {
						if (response.status == play.api.http.Status.OK) {
								parser.parseV1Response(response.body)

						} else {
								logger.error("Error calling recaptcha v1 API, HTTP response " + response.status)
								Left(Error(RecaptchaErrorCode.recaptchaNotReachable))
						}
				}

				} recover {
						case ex: java.io.IOException => {
								logger.error("Unable to call recaptcha v1 API", ex)
								Left(Error(RecaptchaErrorCode.recaptchaNotReachable))
						}
				}
		}

		/**
			* Low level API (independent of Play form and request APIs).
			*
			* Verifies whether a recaptcha response is valid, by invoking the Google Recaptcha API version
			* 2 verify web service in a reactive manner.
			*
			* @param response The recaptcha response, to verify
			* @param remoteIp The IP address of the end user
			* @param context  Implicit - The execution context used for futures
			* @return A future that will be either an Error (with a code) or Success
			* @throws IllegalStateException Developer errors that shouldn't happen - Plugin not present
			*                               or not enabled
			*/
		def verifyV2(response: String, remoteIp: String)(
				implicit context: ExecutionContext): Future[Either[Error, Success]] = {


				import scala.concurrent.duration._

				checkPluginEnabled()

				// create the v2 POST payload
				val payload = Map(
						"secret" -> Seq(
								current.configuration.getString(RecaptchaConfiguration.privateKey).get),
						"response" -> Seq(response),
						"remoteip" -> Seq(remoteIp)
				)

				if (response.equals(WidgetHelper.getSeleniumBypassCode)) {
						Future(Right(Success()))
				} else {
						val requestTimeout = current.configuration.getMilliseconds(
								RecaptchaConfiguration.requestTimeout).getOrElse(10.seconds.toMillis)

						val futureResponse = wsRequest.withRequestTimeout(requestTimeout.toInt).post(payload)

						futureResponse.map { response => {
								if (response.status == play.api.http.Status.OK) {
										parser.parseV2Response(response.json)

								} else {
										logger.error("Error calling recaptcha v2 API, HTTP response " + response.status)
										Left(Error(RecaptchaErrorCode.recaptchaNotReachable))
								}
						}

						} recover {
								case ex: java.io.IOException => {
										logger.error("Unable to call recaptcha v2 API", ex)
										Left(Error(RecaptchaErrorCode.recaptchaNotReachable))
								}

								// e.g. various JSON parsing errors are possible
								case other => {
										logger.error("Error calling recaptcha v2 API: " + other.getMessage())
										Left(Error(RecaptchaErrorCode.apiError))
								}
						}
				}
		}

		/**
			* Checks whether the plugin is enabled.
			*
			* @throws IllegalStateException Developer errors that shouldn't happen - plugin is not
			*                               present, or is not enabled (e.g. because mandatory configuration is missing)
			*/
		private def checkPluginEnabled(): Unit = {
				val plugin = current.plugin[RecaptchaPlugin].getOrElse({
						val message = "Recaptcha Plugin not found"
						logger.error(message)
						throw new IllegalStateException(message)
				})

				if (!plugin.isEnabled) {
						val message =
								"Recaptcha Plugin is not enabled, please see earlier error log messages as to why"
						logger.error(message)
						throw new IllegalStateException(message)
				}
		}
}

/**
	* Used to hold various recaptcha error codes. Some are defined by Google Recaptcha, some are used
	* solely by this module. Yes, the Google Recpatcha documentation states not to rely on these, so
	* we deliberately keep these to a minimum.
	*/
object RecaptchaErrorCode {

		/** Defined in Google Recaptcha documentation, returned by Recaptcha itself, means the captcha was incorrect. */
		val captchaIncorrect = "incorrect-captcha-sol"

		/** Defined in Google Recaptcha documentation, used by this module, means recaptcha itself couldn't be reached. */
		val recaptchaNotReachable = "recaptcha-not-reachable"

		/** An API error (e.g. invalid format response). */
		val apiError = "recaptcha-api-error"

		/** The recaptcha response was missing from the request (probably an end user error). */
		val responseMissing = "recaptcha-response-missing"

		/** Error codes that are only for internal use by this module, and shouldn't be passed to the recaptcha API. */
		val internalErrorCodes = Seq(recaptchaNotReachable, apiError, responseMissing)

		/**
			* Determine whether the specified error code is for internal use only by this module, and shouldn't be passed
			* to the recaptcha API.
			*
			* @param errorCode The error code
			* @return <code>true</code> if internal
			*/
		def isInternalErrorCode(errorCode: String): Boolean = {
				internalErrorCodes.contains(errorCode)
		}
}
