/*
 * Copyright 2016 Chris Nappin
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

import RecaptchaSettings._
import org.specs2.runner.JUnitRunner
import org.junit.runner.RunWith
import org.specs2.specification.Scope
import play.api.{Mode, Environment, Configuration}
import play.api.i18n.{MessagesApi, I18nComponents, Lang}
import play.api.test.{FakeApplication, WithApplication, FakeRequest, PlaySpecification}

/**
 * Tests the <code>WidgetHelper</code> object.
 *
 * @author chrisnappin
 */
@RunWith(classOf[JUnitRunner])
class WidgetHelperSpec extends PlaySpecification {

    val validV2Settings: Map[String, String] =  Map(
        PrivateKeyConfigProp -> "private-key",
        PublicKeyConfigProp -> "public-key",
        RequestTimeoutConfigProp -> "5 seconds")

    abstract class WithWidgetHelper(configProps: Map[String, AnyRef]) extends WithApplication(
        FakeApplication(additionalConfiguration = configProps)) with Scope {

        val settings = new RecaptchaSettings(app.configuration)
        val widgetHelper = new WidgetHelper(settings)
    }

    "getWidgetScriptUrl" should {

        "exclude public key" in new WithWidgetHelper(validV2Settings) {
            implicit val messages = app.injector.instanceOf[MessagesApi].preferred(Seq(Lang("fr")))
            
            widgetHelper.widgetScriptUrl(None) must endWith("api.js")
        }

        "exclude error code if specified" in new WithWidgetHelper(validV2Settings) {
            implicit val messages = app.injector.instanceOf[MessagesApi].preferred(Seq(Lang("fr")))
            
            widgetHelper.widgetScriptUrl(Some("error-code")) must endWith("api.js")
        }

        "exclude language if mode is auto" in new WithWidgetHelper(
                validV2Settings ++ Map(LanguageModeConfigProp -> "auto")) {
            implicit val messages = app.injector.instanceOf[MessagesApi].preferred(Seq(Lang("fr")))
            
            widgetHelper.widgetScriptUrl(None) must endWith("api.js")
        }

        "include language if mode is force" in new WithWidgetHelper(
                validV2Settings ++ Map(LanguageModeConfigProp -> "force",
                        ForceLanguageConfigProp -> "fr")) {
            implicit val messages = app.injector.instanceOf[MessagesApi].preferred(Seq(Lang("fr")))
            
            widgetHelper.widgetScriptUrl(None) must endWith("api.js?hl=fr")
        }

        "include language (only) if mode is play" in new WithWidgetHelper(
                validV2Settings ++ Map(LanguageModeConfigProp -> "play",
                        "play.i18n.langs" -> Seq("fr"))) {
            // no browser locale, should just use the default language set above...
            implicit val messages = app.injector.instanceOf[MessagesApi].preferred(Seq.empty[Lang])

            widgetHelper.widgetScriptUrl(None) must endWith("api.js?hl=fr")
        }

        "include language and country if mode is play" in new WithWidgetHelper(
            validV2Settings ++ Map(LanguageModeConfigProp -> "play",
                "play.i18n.langs" -> Seq("en", "en-US", "en-GB"))) {
            implicit val messages = app.injector.instanceOf[MessagesApi].preferred(Seq(Lang("en", "GB")))

            widgetHelper.widgetScriptUrl(None) must endWith("api.js?hl=en-GB")
        }

        "include just language if mode is play" in new WithWidgetHelper(
            validV2Settings ++ Map(LanguageModeConfigProp -> "play",
                "play.i18n.langs" -> Seq("en", "en-US", "en-GB"))) {
            implicit val messages = app.injector.instanceOf[MessagesApi].preferred(Seq(Lang("en", "US")))

            // en-US isn't a supported country variant, so should just use the language code
            widgetHelper.widgetScriptUrl(None) must endWith("api.js?hl=en")
        }
    }

    "getWidgetNoScriptUrl" should {

        "include public key" in new WithWidgetHelper(validV2Settings) {
            widgetHelper.widgetNoScriptUrl(None) must endWith("fallback?k=public-key")
        }

        "exclude error code if specified" in new WithWidgetHelper(validV2Settings) {
            widgetHelper.widgetNoScriptUrl(Some("error-code")) must
                endWith("fallback?k=public-key")
        }
    }

    "getPublicKey" should {

        "return the public key" in new WithWidgetHelper(validV2Settings) {
            widgetHelper.publicKey must equalTo("public-key")
        }
    }
}
