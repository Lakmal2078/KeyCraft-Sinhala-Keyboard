package buildlogic

import java.security.KeyStore
import java.security.PrivateKey
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class ValidateReleaseSigning : DefaultTask() {
  @get:Internal
  abstract val keystoreFile: RegularFileProperty

  @get:Input
  abstract val keystoreReferenceProvided: Property<Boolean>

  @TaskAction
  fun validate() {
    val storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD").orEmpty()
    val keyAlias = System.getenv("RELEASE_KEY_ALIAS")?.trim().orEmpty()
    val keyPassword = System.getenv("RELEASE_KEY_PASSWORD").orEmpty()
    val keystore = keystoreFile.get().asFile
    val missing = mutableListOf<String>()

    if (!keystoreReferenceProvided.get())
      missing.add("RELEASE_KEYSTORE or RELEASE_KEYSTORE_BASE64")
    if (!keystore.isFile || !keystore.canRead())
      missing.add("a readable RELEASE_KEYSTORE file")
    if (storePassword.isBlank()) missing.add("RELEASE_KEYSTORE_PASSWORD")
    if (keyAlias.isBlank()) missing.add("RELEASE_KEY_ALIAS")
    if (keyPassword.isBlank()) missing.add("RELEASE_KEY_PASSWORD")

    if (missing.isNotEmpty()) {
      throw GradleException(
        "Release signing configuration is incomplete. Missing or invalid: ${missing.joinToString()}"
      )
    }

    val keyStore = listOf("PKCS12", "JKS").firstNotNullOfOrNull { type ->
      runCatching {
        KeyStore.getInstance(type).apply {
          keystore.inputStream().use { load(it, storePassword.toCharArray()) }
        }
      }.getOrNull()
    } ?: throw GradleException(
      "Cannot open RELEASE_KEYSTORE with RELEASE_KEYSTORE_PASSWORD. Check the keystore file and password."
    )

    if (!keyStore.containsAlias(keyAlias)) {
      throw GradleException("RELEASE_KEY_ALIAS '$keyAlias' was not found in the configured keystore.")
    }

    val privateKey = try {
      keyStore.getKey(keyAlias, keyPassword.toCharArray())
    } catch (e: Exception) {
      throw GradleException("RELEASE_KEY_PASSWORD is invalid for alias '$keyAlias'.", e)
    }
    if (privateKey !is PrivateKey) {
      throw GradleException("RELEASE_KEY_ALIAS '$keyAlias' does not identify a private signing key.")
    }
  }
}
