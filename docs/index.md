This is a [Play Framework](http://www.playframework.com) module, for Scala and Play 2.x, to provide integration with [Google reCAPTCHA](http://www.google.com/recaptcha) in a reactive (non-blocking) manner.

* [Release details](#latest-release)
* [Changelog](#changelog)
* [Module Dependency](#module-dependency)
* [How to use](#how-to-use)

## Latest Release

| Module Revision | reCAPTCHA Versions | Play Version | Scala Versions | ScalaDoc |
|:---------------:|:------------------:|:------------:|:--------------:|:--------:|
|1.0              |v1, v2              |2.3.x         |2.10, 2.11      |[ScalaDoc](http://www.javadoc.io/doc/com.nappin/play-recaptcha_2.11/1.0)|

### Changelog

#### Release 1.0
* Added support for reCAPTCHA version 2 (aka no-captcha reCAPTCHA)

#### Release 0.9
* Added full support for internationalisation (Play i18n and reCAPTCHA language and custom strings)
* Added security settings (invoke service and widget using HTTP or HTTPS)

#### Release 0.8
* Initial release

##Module Dependency
The play-recaptcha module is distributed using Maven Central so it can be easily added as a library dependency in your Play Application's SBT build scripts, as follows:

    "com.nappin" %% "play-recaptcha" % "1.0"

##How to use
Please see these examples:

![reCAPTCHA version 1 example](https://raw.githubusercontent.com/chrisnappin/play-recaptcha/master/recaptcha-example-v1.png)

* [example reCAPTCHA v1 application](https://github.com/chrisnappin/play-recaptcha-example/tree/release-1.0)

![reCAPTCHA version 2 example](https://raw.githubusercontent.com/chrisnappin/play-recaptcha/master/recaptcha-example-v2.png)

* [example reCAPTCHA v2 application](https://github.com/chrisnappin/play-recaptcha-v2-example/tree/release-1.0)

for ready-to-use internationalised Scala Play 2.x web applications using this module. You can download this code and run it in Play, or you can follow the instructions below to add this module to an existing web application.

The play-recaptcha module comes with two APIs:
* [High Level API](high-level-api.md) - that integrates with Play's Form APIs
* [Low Level API](low-level-api.md) - that has no such dependency

Unless you are using a non-standard approach in your Play 2 Scala applications, the High Level API is the one to use - it is much simpler and requires much less code.