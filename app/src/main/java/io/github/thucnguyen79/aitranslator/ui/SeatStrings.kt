package io.github.thucnguyen79.aitranslator.ui

import io.github.thucnguyen79.aitranslator.core.model.Lang

/**
 * Words shown in one half of the conversation, in that half's language: each person reads the buttons
 * and status of their own side whatever language the app itself is in.
 */
data class SeatStrings(
    val speak: String,
    val stop: String,
    val type: String,
    val hideKeyboard: String,
    val clear: String,
    val replay: String,
    val placeholder: String,
    val listening: String,
    val otherSpeaking: String,
    val recognizing: String,
    val translating: String,
    val speakingTranslation: String,
    val preparing: String,
    val nothingHeard: String,
    val error: String,
    val translatingLater: String,
    val packMissing: String,
    val openPacks: String,
    val noOfflineVoice: String,
    val rotate: String,
    val copy: String,
    val copied: String,
    val close: String,
    val clearTitle: String,
    val clearMessage: String,
    val cancel: String,
    val installVoice: String,
    val backupVoice: String,
    val readAloud: String,
    val phrasebook: String,
    val edit: String,
    val save: String,
    /** Label for a translation translated back into the speaker's language (F4.5). */
    val backTranslation: String,
) {
    companion object {
        fun of(lang: Lang): SeatStrings = when (lang) {
            Lang.VI -> SeatStrings(
                speak = "Nói", stop = "Dừng", type = "Nhập", hideKeyboard = "Ẩn bàn phím", clear = "Xoá",
                replay = "Nghe lại", placeholder = "Nhập câu", listening = "Đang nghe… nói xong sẽ tự dừng",
                otherSpeaking = "Người kia đang nói…", recognizing = "Đang nhận dạng…", translating = "Đang dịch…",
                speakingTranslation = "Đang đọc bản dịch…", preparing = "Đang nạp mô hình…",
                nothingHeard = "Không nghe rõ, hãy nói lại", error = "Lỗi", translatingLater = "đang dịch…",
                packMissing = "Cần tải gói ngôn ngữ", openPacks = "Mở Gói ngôn ngữ",
                noOfflineVoice = "Máy chưa có giọng đọc offline cho ngôn ngữ này",
                rotate = "Xoay", copy = "Sao chép", copied = "Đã sao chép", close = "Đóng",
                clearTitle = "Xoá hội thoại?", clearMessage = "Các câu sẽ được xoá khỏi màn hình. Bản đã lưu vẫn còn trong Lịch sử (trừ khi bật chế độ riêng tư).", cancel = "Huỷ",
                installVoice = "Cài giọng", backupVoice = "Tải giọng dự phòng", readAloud = "Đọc bản dịch",
                phrasebook = "Câu mẫu", edit = "Sửa", save = "Lưu", backTranslation = "Dịch ngược",
            )
            Lang.EN -> SeatStrings(
                speak = "Speak", stop = "Stop", type = "Type", hideKeyboard = "Hide keyboard", clear = "Clear",
                replay = "Replay", placeholder = "Type a sentence", listening = "Listening… stops when you finish",
                otherSpeaking = "The other person is speaking…", recognizing = "Recognizing…", translating = "Translating…",
                speakingTranslation = "Reading the translation…", preparing = "Loading models…",
                nothingHeard = "Didn't catch that, please say it again", error = "Error", translatingLater = "translating…",
                packMissing = "Language pack needed", openPacks = "Open language packs",
                noOfflineVoice = "No offline voice for this language on this phone",
                rotate = "Rotate", copy = "Copy", copied = "Copied", close = "Close",
                clearTitle = "Clear the conversation?", clearMessage = "The sentences will be removed from the screen. The saved copy stays in History (unless private mode is on).", cancel = "Cancel",
                installVoice = "Install voice", backupVoice = "Get backup voice", readAloud = "Read translations aloud",
                phrasebook = "Phrases", edit = "Edit", save = "Save", backTranslation = "Back-translation",
            )
            Lang.ZH -> SeatStrings(
                speak = "说话", stop = "停止", type = "输入", hideKeyboard = "收起键盘", clear = "清除",
                replay = "重播", placeholder = "输入句子", listening = "正在听… 说完会自动停止",
                otherSpeaking = "对方正在说话…", recognizing = "正在识别…", translating = "正在翻译…",
                speakingTranslation = "正在朗读译文…", preparing = "正在加载模型…",
                nothingHeard = "没听清，请再说一遍", error = "错误", translatingLater = "翻译中…",
                packMissing = "需要下载语言包", openPacks = "打开语言包",
                noOfflineVoice = "本机没有该语言的离线语音",
                rotate = "旋转", copy = "复制", copied = "已复制", close = "关闭",
                clearTitle = "清除对话？", clearMessage = "屏幕上的句子将被清除。已保存的记录仍在“历史”中（除非开启了隐私模式）。", cancel = "取消",
                installVoice = "安装语音", backupVoice = "下载备用语音", readAloud = "朗读译文",
                phrasebook = "常用语", edit = "修改", save = "保存", backTranslation = "回译",
            )
            Lang.KO -> SeatStrings(
                speak = "말하기", stop = "중지", type = "입력", hideKeyboard = "키보드 숨기기", clear = "지우기",
                replay = "다시 듣기", placeholder = "문장 입력", listening = "듣는 중… 말을 마치면 자동으로 멈춥니다",
                otherSpeaking = "상대방이 말하는 중…", recognizing = "인식 중…", translating = "번역 중…",
                speakingTranslation = "번역을 읽는 중…", preparing = "모델 불러오는 중…",
                nothingHeard = "잘 듣지 못했어요. 다시 말해 주세요", error = "오류", translatingLater = "번역 중…",
                packMissing = "언어 팩이 필요합니다", openPacks = "언어 팩 열기",
                noOfflineVoice = "이 기기에 이 언어의 오프라인 음성이 없습니다",
                rotate = "회전", copy = "복사", copied = "복사됨", close = "닫기",
                clearTitle = "대화를 지울까요?", clearMessage = "화면의 문장이 지워집니다. 저장된 기록은 '기록'에 남습니다 (비공개 모드가 아닌 경우).", cancel = "취소",
                installVoice = "음성 설치", backupVoice = "예비 음성 받기", readAloud = "번역 읽어 주기",
                phrasebook = "자주 쓰는 말", edit = "수정", save = "저장", backTranslation = "역번역",
            )
            Lang.JA -> SeatStrings(
                speak = "話す", stop = "停止", type = "入力", hideKeyboard = "キーボードを閉じる", clear = "消去",
                replay = "もう一度聞く", placeholder = "文を入力", listening = "聞いています… 話し終わると自動で止まります",
                otherSpeaking = "相手が話しています…", recognizing = "認識中…", translating = "翻訳中…",
                speakingTranslation = "翻訳を読み上げ中…", preparing = "モデルを読み込み中…",
                nothingHeard = "聞き取れませんでした。もう一度話してください", error = "エラー", translatingLater = "翻訳中…",
                packMissing = "言語パックが必要です", openPacks = "言語パックを開く",
                noOfflineVoice = "この端末にはこの言語のオフライン音声がありません",
                rotate = "回転", copy = "コピー", copied = "コピーしました", close = "閉じる",
                clearTitle = "会話を消去しますか？", clearMessage = "画面の文が消去されます。保存された記録は「履歴」に残ります（プライベートモードでない場合）。", cancel = "キャンセル",
                installVoice = "音声をインストール", backupVoice = "予備の音声を入手", readAloud = "翻訳を読み上げる",
                phrasebook = "定型文", edit = "修正", save = "保存", backTranslation = "逆翻訳",
            )
        }
    }
}
