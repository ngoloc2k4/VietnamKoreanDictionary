package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.initial.PreloadedWords
import com.example.data.model.parseExamples
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Từ Điển Hàn Việt", appName)
  }

  @Test
  fun `verify preloaded words integrity`() {
    val words = PreloadedWords.getInitialWords()
    assertTrue("Preloaded words should not be empty", words.isNotEmpty())

    // Check first word
    val firstWord = words.first()
    assertNotNull(firstWord.korean)
    assertNotNull(firstWord.vietnamese)
    assertTrue("Word should have root form", firstWord.rootForm.isNotBlank())
    assertTrue("Word should have present tense", firstWord.presentTense.isNotBlank())
    assertTrue("Word should have formal honorific", firstWord.formalHigh.isNotBlank())
    assertTrue("Word should have exam frequency", firstWord.examFrequency.isNotBlank())

    val examples = firstWord.parseExamples()
    assertTrue("Examples should be parsed correctly", examples.isNotEmpty())
    assertNotNull(examples.first().korean)
    assertNotNull(examples.first().vietnamese)
  }

  @Test
  fun `verify room pre-packaged database opens without schema error`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getDatabase(context)
    val count = db.wordDao().getCount()
    assertTrue("Database should contain words (actual: $count)", count >= 10)
  }
}
