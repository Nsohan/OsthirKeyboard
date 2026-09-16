import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.io.FileOutputStream

plugins {
  id("com.android.application") version "8.13.2"
}

dependencies {
  // Following versions of androidx.window require sdk version 23
  implementation("androidx.window:window-java:1.4.0")
  implementation("androidx.core:core:1.16.0") // Version 1.17.0 available with sdk 36
  // Used by SettingsActivity for the Material-styled preference screen.
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("androidx.preference:preference:1.2.1")
  implementation("com.google.android.material:material:1.12.0")
  implementation("com.github.bumptech.glide:glide:4.16.0")
  testImplementation("junit:junit:4.13.2")
}

android {
  namespace = "com.nhs.customkeyboard"
  compileSdkVersion = "android-36"

  defaultConfig {
    applicationId = "com.nhs.customkeyboard"
    minSdk = 21
    targetSdk { version = release(36) }
    versionCode = 4
    versionName = "0.0.04"
  }

  sourceSets {
    named("main") {
      manifest.srcFile("AndroidManifest.xml")
      java.srcDirs("srcs/com.nhs.customkeyboard", "vendor/cdict/java/nhs.cdict")
      res.srcDirs("res", "build/generated-resources")
      assets.srcDirs("assets", "build/generated-assets")
    }

    named("test") {
      java.srcDirs("test")
    }
  }

  externalNativeBuild {
    ndkBuild {
      path = file("vendor/Android.mk")
    }
  }

  signingConfigs {
    // Debug builds will always be signed. If no environment variables are set, a default
    // keystore will be initialized by the task initDebugKeystore and used. This keystore
    // can be uploaded to GitHub secrets by following instructions in CONTRIBUTING.md
    // in order to always receive correctly signed debug APKs from the CI.
    named("debug") {
      storeFile = file(System.getenv("DEBUG_KEYSTORE") ?: "debug.keystore")
      storePassword = System.getenv("DEBUG_KEYSTORE_PASSWORD") ?: "debug0"
      keyAlias = System.getenv("DEBUG_KEY_ALIAS") ?: "debug"
      keyPassword = System.getenv("DEBUG_KEY_PASSWORD") ?: "debug0"
    }

    create("release") {
      val ks = System.getenv("RELEASE_KEYSTORE")
      val localKs = file("release.keystore")
      if (ks != null) {
        storeFile = file(ks)
        storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
        keyAlias = System.getenv("RELEASE_KEY_ALIAS")
        keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
      } else if (localKs.exists()) {
        storeFile = localKs
        storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: "nhkeyboard123"
        keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "nhcustomkeyboard"
        keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "nhkeyboard123"
      }
    }
  }

  buildTypes {
    named("release") {
      isMinifyEnabled = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro")
      isShrinkResources = true
      isDebuggable = false
      resValue("string", "app_name", "@string/app_name_release")
      signingConfig = signingConfigs["release"]
    }

    named("debug") {
      isMinifyEnabled = false
      isShrinkResources = false
      isDebuggable = true
      applicationIdSuffix = ".debug"
      resValue("string", "app_name", "@string/app_name_debug")
      resValue("bool", "debug_logs", "true")
      signingConfig = signingConfigs["debug"]
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
}


// This raises an error with an informative message instead of the confusing
// ndk-build errors that occur when submodules are not initialized.
gradle.projectsEvaluated {
  if (!file("vendor/cdict/java").exists())
    throw GradleException("Git submodules not initialized. Run 'git submodule update --init'")
}

val buildKeyboardFont by tasks.registering(Exec::class) {
  val `in` = projectDir.resolve("srcs/special_font")
  val out = layout.projectDirectory.file("assets/special_font.ttf")
  inputs.dir(`in`)
  outputs.file(out)
  doFirst { println("\nBuilding assets/special_font.ttf") }
  workingDir = `in`
  val svgFiles = `in`.listFiles()!!.filter {
    it.isFile && it.name.endsWith(".svg")
  }.toTypedArray()
  commandLine("fontforge", "-lang=ff", "-script", "build.pe", out.asFile.absolutePath, *svgFiles)
}

val genEmojis by tasks.registering(Exec::class) {
  doFirst { println("\nGenerating res/raw/emojis.txt") }
  workingDir = projectDir
  commandLine("python", "gen_emoji.py")
}

val genLayoutsList by tasks.registering(Exec::class) {
  inputs.dir(projectDir.resolve("srcs/layouts"))
  outputs.file(projectDir.resolve("res/values/layouts.xml"))
  doFirst { println("\nGenerating res/values/layouts.xml") }
  workingDir = projectDir
  commandLine("python", "gen_layouts.py")
}

val genMethodXml by tasks.registering(Exec::class) {
  val out = projectDir.resolve("res/xml/method.xml")
  inputs.file(projectDir.resolve("gen_method_xml.py"))
  inputs.file(projectDir.resolve("res/values/dictionaries.xml"))
  outputs.file(out)
  doFirst { println("\nGenerating res/xml/method.xml") }
  doFirst { standardOutput = FileOutputStream(out) }
  workingDir = projectDir
  commandLine("python", "gen_method_xml.py")
}

val checkKeyboardLayouts by tasks.registering(Exec::class) {
  inputs.dir(projectDir.resolve("srcs/layouts"))
  inputs.file(projectDir.resolve("srcs/com.nhs.customkeyboard/KeyValue.java"))
  outputs.file(projectDir.resolve("check_layout.output"))
  doFirst { println("\nChecking layouts") }
  workingDir = projectDir
  commandLine("python", "check_layout.py")
}

val compileComposeSequences by tasks.registering(Exec::class) {
  val `in` = projectDir.resolve("srcs/compose")
  val out = projectDir.resolve("srcs/com.nhs.customkeyboard/ComposeKeyData.java")
  inputs.dir(`in`)
  outputs.file(out)
  doFirst { println("\nGenerating $out") }
  val sequences = `in`.listFiles { it: File ->
    it.name.endsWith(".json")
  }!!.map { it.absolutePath }.toTypedArray()
  workingDir = projectDir
  commandLine("python", `in`.resolve("compile.py").absolutePath, *sequences)
  doFirst { standardOutput = FileOutputStream(out) }
}

tasks.withType(Test::class).configureEach {
  dependsOn(genLayoutsList, checkKeyboardLayouts, genMethodXml)
}

val initDebugKeystore by tasks.registering(Exec::class) {
  doFirst { println("Initializing default debug keystore") }
  isEnabled = !file("debug.keystore").exists()
  // A shell script might be needed if this line requires input from the user
  commandLine("keytool", "-genkeypair", "-dname", "cn=d, ou=e, o=b, c=ug", "-alias", "debug", "-keypass", "debug0", "-keystore", "debug.keystore", "-keyalg", "rsa", "-storepass", "debug0", "-validity", "10000")
}

// latn_qwerty_us and latn_qwerty_us_custom are used as raw resources by layout options.
val copyRawQwertyUS by tasks.registering(Copy::class) {
  from("srcs/layouts/latn_qwerty_us.xml", "srcs/layouts/latn_qwerty_us_custom.xml")
  into("build/generated-resources/raw")
}

val copyLayoutDefinitions by tasks.registering(Copy::class) {
  from("srcs/layouts")
  include("*.xml")
  into("build/generated-resources/xml")
}

val copyLayoutAssets by tasks.registering(Copy::class) {
  from("srcs/layouts")
  include("*.xml")
  into("build/generated-assets/layouts")
}

tasks.named("preBuild") {
  dependsOn(initDebugKeystore, copyRawQwertyUS, copyLayoutDefinitions, copyLayoutAssets)
  // 'mustRunAfter' defines ordering between tasks (which is required by
  // Gradle) but doesn't create a dependency. These rules update files that are
  // checked in the repository that don't need to be updated during regular
  // builds.
  mustRunAfter(genEmojis, genLayoutsList, compileComposeSequences, genMethodXml)
}
