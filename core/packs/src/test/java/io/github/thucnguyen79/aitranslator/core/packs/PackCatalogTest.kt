package io.github.thucnguyen79.aitranslator.core.packs

import io.github.thucnguyen79.aitranslator.core.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PackCatalogTest {
    private val catalog = PackCatalog.parse(File("src/main/assets/packs.json").readText())

    @Test fun everyLanguageHasAPack() {
        for (lang in Lang.entries) assertEquals(listOf(lang), catalog.languagePack(lang).langs)
    }

    @Test fun requirementsComeFirst() {
        assertEquals(listOf("core", "speech-multi", "zh"), catalog.withRequirements("zh").map { it.id })
        assertEquals(listOf("core", "vi"), catalog.withRequirements("vi").map { it.id })
    }

    @Test fun everyFileIsPinned() {
        for (f in catalog.packs.flatMap { it.files }) {
            assertTrue(f.url, f.url.startsWith("https://"))
            assertTrue(f.url, Regex("[0-9a-f]{64}").matches(f.sha256))
            assertTrue(f.url, f.size > 0)
        }
    }

    @Test fun bergamotFoldersMatchTheLocator() {
        // ModelLocator expects mt/<key>/ inside the non-English language's pack.
        for (lang in Lang.entries.filter { it != Lang.EN }) {
            val dirs = catalog.languagePack(lang).files.map { it.path.split('/').take(2).joinToString("/") }.filter { it.startsWith("mt/") }
            assertEquals(setOf("mt/${lang.code}en", "mt/en${lang.code}"), dirs.toSet())
        }
    }
}
