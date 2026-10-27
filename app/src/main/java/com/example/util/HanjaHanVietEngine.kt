package com.example.util

import com.example.data.model.WordEntity

data class HanjaMorpheme(
    val hanja: String,          // e.g. "學"
    val hanViet: String,        // e.g. "Học"
    val hanKor: String,         // e.g. "학"
    val meaning: String,        // e.g. "Học hỏi, tiếp thu tri thức"
    val commonCompounds: List<CompoundSample> = emptyList()
)

data class CompoundSample(
    val korean: String,         // e.g. "학교"
    val hanja: String,          // e.g. "學校"
    val hanViet: String,        // e.g. "Học hiệu"
    val vietnamese: String      // e.g. "Trường học"
)

data class PhoneticRule(
    val title: String,
    val vietnamesePattern: String,
    val koreanPattern: String,
    val explanation: String,
    val examples: List<String>
)

object HanjaHanVietEngine {

    // Common Core Hanja Roots
    private val CORE_MORPHEMES = listOf(
        HanjaMorpheme("學", "Học", "학", "Học hỏi, tri thức, trường lớp", listOf(
            CompoundSample("학교", "學校", "Học hiệu", "Trường học"),
            CompoundSample("학생", "學生", "Học sinh", "Học sinh, sinh viên"),
            CompoundSample("학습", "學習", "Học tập", "Học tập, rèn luyện"),
            CompoundSample("학자", "學者", "Học giả", "Nhà nghiên cứu, học giả"),
            CompoundSample("유학", "留學", "Lưu học", "Du học"),
            CompoundSample("문학", "文學", "Văn học", "Văn học"),
            CompoundSample("수학", "數學", "Số học", "Môn toán, toán học")
        )),
        HanjaMorpheme("生", "Sinh", "생", "Sống, sinh ra, đời sống", listOf(
            CompoundSample("선생", "先生", "Tiên sinh", "Thầy cô giáo, quý ngài"),
            CompoundSample("학생", "學生", "Học sinh", "Học sinh, sinh viên"),
            CompoundSample("생활", "生活", "Sinh hoạt", "Đời sống thường ngày"),
            CompoundSample("인생", "人生", "Nhân sinh", "Cuộc đời con người"),
            CompoundSample("생일", "生日", "Sinh nhật", "Ngày sinh nhật"),
            CompoundSample("생산", "生産", "Sinh sản", "Sản xuất hàng hóa")
        )),
        HanjaMorpheme("國", "Quốc", "국", "Đất nước, quốc gia", listOf(
            CompoundSample("한국", "韓國", "Hàn Quốc", "Đất nước Hàn Quốc"),
            CompoundSample("외국", "外國", "Ngoại quốc", "Nước ngoài"),
            CompoundSample("국제", "國際", "Quốc tế", "Quốc tế, toàn cầu"),
            CompoundSample("국민", "國民", "Quốc dân", "Người dân trong nước"),
            CompoundSample("국가", "國家", "Quốc gia", "Nhà nước, tổ quốc")
        )),
        HanjaMorpheme("人", "Nhân", "인", "Con người, nhân loại", listOf(
            CompoundSample("인간", "人間", "Nhân gian", "Con người"),
            CompoundSample("외국인", "外國人", "Ngoại quốc nhân", "Người nước ngoài"),
            CompoundSample("인구", "人口", "Nhân khẩu", "Dân số"),
            CompoundSample("인기", "人氣", "Nhân khí", "Sự mến mộ, nổi tiếng"),
            CompoundSample("주인", "主人", "Chủ nhân", "Người chủ, gia chủ")
        )),
        HanjaMorpheme("心", "Tâm", "심", "Trái tim, lòng dạ, tâm trí", listOf(
            CompoundSample("심리", "心理", "Tâm lý", "Tâm lý học"),
            CompoundSample("관심", "關心", "Quan tâm", "Sự quan tâm, chú ý"),
            CompoundSample("안심", "安心", "An tâm", "Sự yên tâm, nhẹ nhõm"),
            CompoundSample("결심", "決心", "Quyết tâm", "Quyết định kiên định")
        )),
        HanjaMorpheme("功", "Công", "공", "Công sức, thành quả, nỗ lực", listOf(
            CompoundSample("공부", "功夫", "Công phu", "Học tập, bỏ công sức"),
            CompoundSample("성공", "成功", "Thành công", "Đạt được mục tiêu")
        )),
        HanjaMorpheme("成", "Thành", "성", "Trở nên, hoàn thành, tạo dựng", listOf(
            CompoundSample("성공", "成功", "Thành công", "Thành công"),
            CompoundSample("완성", "完成", "Hoàn thành", "Làm xong trọn vẹn"),
            CompoundSample("성장", "成長", "Thành trưởng", "Sự tăng trưởng, lớn khôn")
        )),
        HanjaMorpheme("幸", "Hạnh", "행", "May mắn, hạnh phúc", listOf(
            CompoundSample("행복", "幸福", "Hạnh phúc", "Niềm vui trọn vẹn"),
            CompoundSample("다행", "多幸", "Đa hạnh", "May mắn thay")
        )),
        HanjaMorpheme("福", "Phúc", "복", "Điều tốt lành, phước đức", listOf(
            CompoundSample("행복", "幸福", "Hạnh phúc", "Hạnh phúc"),
            CompoundSample("복권", "福券", "Phúc khoán", "Vé số, vé may mắn")
        )),
        HanjaMorpheme("經", "Kinh", "경", "Kinh qua, quản lý, đường lối", listOf(
            CompoundSample("경제", "經濟", "Kinh tế", "Nền kinh tế, tài chính"),
            CompoundSample("경험", "經驗", "Kinh nghiệm", "Trải nghiệm thực tế"),
            CompoundSample("경영", "經營", "Kinh doanh", "Quản trị kinh doanh")
        )),
        HanjaMorpheme("濟", "Tế", "제", "Cứu giúp, giúp đời, điều hòa", listOf(
            CompoundSample("경제", "經濟", "Kinh tế", "Kinh tế"),
            CompoundSample("구제", "救濟", "Cứu tế", "Cứu trợ, giúp đỡ")
        )),
        HanjaMorpheme("時", "Thời", "시", "Thời gian, thời khắc, giờ", listOf(
            CompoundSample("시간", "時間", "Thời gian", "Thời gian, tiếng đồng hồ"),
            CompoundSample("시대", "時代", "Thời đại", "Giai đoạn lịch sử"),
            CompoundSample("동시", "同時", "Đồng thời", "Cùng một lúc")
        )),
        HanjaMorpheme("間", "Gian", "간", "Khoảng cách, ở giữa, thời khắc", listOf(
            CompoundSample("시간", "時間", "Thời gian", "Khoảng thời gian"),
            CompoundSample("인간", "人間", "Nhân gian", "Loài người"),
            CompoundSample("공간", "空間", "Không gian", "Khoảng không gian")
        )),
        HanjaMorpheme("問", "Vấn", "문", "Hỏi, thắc mắc, câu hỏi", listOf(
            CompoundSample("문제", "問題", "Vấn đề", "Câu hỏi kiểm tra, sự việc"),
            CompoundSample("질문", "質問", "Chất vấn", "Câu hỏi đặt ra"),
            CompoundSample("방문", "訪問", "Phóng vấn", "Đến thăm viếng")
        )),
        HanjaMorpheme("題", "Đề", "제", "Chủ đề, tiêu đề, bài thi", listOf(
            CompoundSample("문제", "問題", "Vấn đề", "Vấn đề, bài thi"),
            CompoundSample("제목", "題目", "Đề mục", "Tựa đề, tiêu đề"),
            CompoundSample("화제", "話題", "Thoại đề", "Chủ đề bàn luận")
        )),
        HanjaMorpheme("增", "Tăng", "증", "Thêm vào, tăng lên", listOf(
            CompoundSample("증가", "增加", "Tăng gia", "Gia tăng số lượng"),
            CompoundSample("증대", "增大", "Tăng đại", "Lớn mạnh thêm")
        )),
        HanjaMorpheme("減", "Giảm", "감", "Bớt đi, sụt giảm", listOf(
            CompoundSample("감소", "減少", "Giảm thiểu", "Sụt giảm số lượng"),
            CompoundSample("감축", "減縮", "Giảm súc", "Cắt giảm, thu nhỏ")
        )),
        HanjaMorpheme("會", "Hội", "회", "Họp mặt, tụ hội, tổ chức", listOf(
            CompoundSample("회사", "會社", "Hội xã", "Công ty doanh nghiệp"),
            CompoundSample("사회", "社會", "Xã hội", "Cộng đồng xã hội"),
            CompoundSample("기회", "機會", "Cơ hội", "Thời cơ thuận lợi")
        )),
        HanjaMorpheme("社", "Xã", "사", "Cộng đồng, nơi chốn, đền miếu", listOf(
            CompoundSample("회사", "會社", "Hội xã", "Công ty"),
            CompoundSample("사회", "社會", "Xã hội", "Xã hội"),
            CompoundSample("신문사", "新聞社", "Tân văn xã", "Tòa soạn báo")
        )),
        HanjaMorpheme("機", "Cơ", "기", "Cơ may, máy móc, then chốt", listOf(
            CompoundSample("기회", "機會", "Cơ hội", "Thời cơ"),
            CompoundSample("비행기", "飛行機", "Phi hành cơ", "Máy bay"),
            CompoundSample("기계", "機械", "Cơ giới", "Máy móc")
        ))
    )

    // Phonetic mapping rules between Sino-Vietnamese & Sino-Korean
    val PHONETIC_RULES = listOf(
        PhoneticRule(
            title = "Quy tắc 1: Phụ âm [H-] (Việt) ⇄ [H-] (Hàn) hoặc Nguyên âm",
            vietnamesePattern = "H- (Học, Hạnh, Hợp, Hoa)",
            koreanPattern = "H- (학, 행, 합) hoặc Wa- (화)",
            explanation = "Đa số các từ Hán-Việt bắt đầu bằng chữ H sẽ tương ứng với âm ㅎ (h) trong tiếng Hàn.",
            examples = listOf("Học (學) → 학 (Hak)", "Hạnh (幸) → 행 (Haeng)", "Hợp (合) → 합 (Hap)", "Hi vọng (希望) → 희망 (Hui-mang)")
        ),
        PhoneticRule(
            title = "Quy tắc 2: Phụ âm [T- / Th-] (Việt) ⇄ [S- / ㅅ] (Hàn)",
            vietnamesePattern = "T-, Th- (Sinh, Thành, Thực, Thân, Tâm)",
            koreanPattern = "S- (ㅅ) (생, 성, 식, 신, 심)",
            explanation = "Quy luật phổ biến nhất: Tiếng Việt đọc là T hoặc Th thì tiếng Hàn gần như chuyển thành âm S (ㅅ).",
            examples = listOf("Thành công (成功) → 성공 (Seong-gong)", "Học sinh (學生) → 학생 (Hak-saeng)", "Thực đường/Quán ăn (食堂) → 식당 (Sik-ttang)", "Tâm lý (心理) → 심리 (Sim-ni)")
        ),
        PhoneticRule(
            title = "Quy tắc 3: Phụ âm [C- / K- / Qu-] (Việt) ⇄ [G- / K- / ㄱ] (Hàn)",
            vietnamesePattern = "C-, K-, Qu- (Quốc, Công, Kinh, Cơ, Cảm)",
            koreanPattern = "G- / K- (ㄱ) (국, 공, 경, 기, 감)",
            explanation = "Các âm vòm họng C/K/Qu trong tiếng Việt tương ứng mật thiết với phụ âm ㄱ (G/K) tiếng Hàn.",
            examples = listOf("Quốc gia (國家) → 국가 (Guk-ga)", "Kinh tế (經濟) → 경제 (Gyeong-je)", "Cơ hội (機會) → 기회 (Gi-hoe)", "Cảm ơn/Cảm tạ (感謝) → 감사 (Gam-sa)")
        ),
        PhoneticRule(
            title = "Quy tắc 4: Phụ âm [Nh- / N-] (Việt) ⇄ [I- / Y- / ㅇ] (Hàn)",
            vietnamesePattern = "Nh-, N- (Nhân, Nhật, Nhập, Nhiệt)",
            koreanPattern = "Nguyên âm không phụ âm đầu (ㅇ) (인, 일, 입, 열)",
            explanation = "Âm Nh trong Hán-Việt biến thành nguyên âm đứng đầu (phụ âm câm ㅇ) trong tiếng Hàn.",
            examples = listOf("Nhân khẩu/Dân số (人口) → 인구 (In-gu)", "Sinh nhật (生日) → 생일 (Saeng-il)", "Nhập học (入學) → 입학 (Ip-hak)")
        ),
        PhoneticRule(
            title = "Quy tắc 5: Phụ âm [Đ-] (Việt) ⇄ [D- / T- / ㄷ] (Hàn)",
            vietnamesePattern = "Đ- (Đại, Đồng, Đạo, Đề)",
            koreanPattern = "D- / T- (ㄷ) (대, 동, 도, 제)",
            explanation = "Âm Đ trong tiếng Việt chuyển đổi thành âm ㄷ (D/T) trong tiếng Hàn.",
            examples = listOf("Đại học (大學) → 대학 (Dae-hak)", "Đồng thời (同時) → 동시 (Dong-si)", "Đường xá/Đạo lộ (道路) → 도로 (Do-ro)")
        )
    )

    /**
     * Search Hanja morphemes by Sino-Vietnamese reading (âm Hán-Việt),
     * Sino-Korean reading (âm Hán-Hàn), or Chinese Character (chữ Hán).
     */
    fun searchMorphemes(query: String, localDbWords: List<WordEntity>): List<HanjaMorpheme> {
        val q = query.trim().lowercase()
        val qNoAccent = KoreanVietnameseSearchMatcher.removeVietnameseAccents(query)

        // 1. Match from core pre-indexed morphemes
        val matchedCore = CORE_MORPHEMES.filter { item ->
            item.hanja.contains(query.trim()) ||
            item.hanKor == query.trim() ||
            item.hanViet.lowercase() == q ||
            KoreanVietnameseSearchMatcher.removeVietnameseAccents(item.hanViet) == qNoAccent
        }

        // 2. Discover additional compounds dynamically from the local 78,000 DB
        val dynamicCompounds = mutableListOf<CompoundSample>()
        for (w in localDbWords.take(500)) {
            if (w.hanja.isNotBlank() && w.hanViet.isNotBlank()) {
                val matchesHanja = w.hanja.contains(query.trim())
                val matchesHanViet = KoreanVietnameseSearchMatcher.removeVietnameseAccents(w.hanViet).contains(qNoAccent)
                val matchesKor = w.korean.contains(query.trim())

                if (matchesHanja || matchesHanViet || matchesKor) {
                    dynamicCompounds.add(
                        CompoundSample(w.korean, w.hanja, w.hanViet, w.vietnamese)
                    )
                }
            }
        }

        if (matchedCore.isNotEmpty()) {
            return matchedCore.map { core ->
                val combined = (core.commonCompounds + dynamicCompounds).distinctBy { it.korean }
                core.copy(commonCompounds = combined.take(12))
            }
        }

        // If not in core, but found in local DB:
        if (dynamicCompounds.isNotEmpty()) {
            val sample = dynamicCompounds.first()
            return listOf(
                HanjaMorpheme(
                    hanja = sample.hanja.take(1),
                    hanViet = sample.hanViet.split(" ").firstOrNull() ?: query,
                    hanKor = sample.korean.take(1),
                    meaning = "Gốc từ ghép Hán-Việt và Hán-Hàn tương ứng",
                    commonCompounds = dynamicCompounds.distinctBy { it.korean }.take(10)
                )
            )
        }

        return emptyList()
    }

    fun getAllCoreMorphemes(): List<HanjaMorpheme> = CORE_MORPHEMES
}
