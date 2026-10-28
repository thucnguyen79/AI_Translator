package io.github.thucnguyen79.aitranslator.spike.asr

import io.github.thucnguyen79.aitranslator.spike.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class AsrTextTest {
    private fun vi(s: String) = AsrText.restore(s, Lang.VI)

    @Test fun statementGetsSentenceCaseAndPeriod() {
        assertEquals("Xin chào.", vi("XIN CHÀO"))
        assertEquals("Cảm ơn bạn rất nhiều.", vi("CẢM ƠN BẠN RẤT NHIỀU"))
    }

    @Test fun questionsGetQuestionMark() {
        assertEquals("Sao bà không hiểu tôi nói tiếng Việt à?", vi("SAO BÀ KHÔNG HIỂU TÔI NÓI TIẾNG VIỆT À"))
        assertEquals("Bạn tên là gì?", vi("BẠN TÊN LÀ GÌ"))
        assertEquals("Chào bạn, hôm nay bạn có khỏe không?", vi("CHÀO BẠN HÔM NAY BẠN CÓ KHỎE KHÔNG"))
        assertEquals("Cái này giá bao nhiêu?", vi("CÁI NÀY GIÁ BAO NHIÊU"))
    }

    @Test fun openersGetComma() {
        assertEquals("Xin lỗi, bạn có nói được tiếng Anh không?", vi("XIN LỖI BẠN CÓ NÓI ĐƯỢC TIẾNG ANH KHÔNG"))
        assertEquals("Xin chào, rất vui được gặp bạn.", vi("XIN CHÀO RẤT VUI ĐƯỢC GẶP BẠN"))
        // A bare greeting stays as is.
        assertEquals("Chào bạn.", vi("CHÀO BẠN"))
        // "chào" only counts as an opener before a pronoun.
        assertEquals("Chào mừng bạn đến Việt Nam.", vi("CHÀO MỪNG BẠN ĐẾN VIỆT NAM"))
    }

    @Test fun properNounsAreCapitalised() {
        assertEquals("Tôi đến từ Việt Nam.", vi("TÔI ĐẾN TỪ VIỆT NAM"))
        assertEquals("Tôi muốn mua hai vé đi Busan.", vi("TÔI MUỐN MUA HAI VÉ ĐI BUSAN"))
    }

    @Test fun wordBoundariesAreRespected() {
        // "huế" inside another word must not be touched; "không" only counts as a whole word at the end.
        assertEquals("Thuế ở đây cao.", vi("THUẾ Ở ĐÂY CAO"))
    }

    @Test fun casedOutputIsLeftAlone() {
        assertEquals("Are you okay, Can you hear me.", AsrText.restore("Are you okay, Can you hear me.", Lang.EN))
        assertEquals("你好，很高兴认识你。", AsrText.restore("你好，很高兴认识你。", Lang.ZH))
    }
}
