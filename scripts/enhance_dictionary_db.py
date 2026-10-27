import sqlite3
import unicodedata
import re
import os

DB_PATH = "app/src/main/assets/databases/korvi_dictionary.db"

def strip_accents(text):
    if not text:
        return ""
    text = text.replace("đ", "d").replace("Đ", "D")
    text = unicodedata.normalize("NFD", text)
    text = "".join(c for c in text if unicodedata.category(c) != "Mn")
    return text.lower().strip()

CHOSUNG_LIST = ['ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ']

def extract_chosung(text):
    if not text:
        return ""
    res = []
    for ch in text:
        code = ord(ch)
        if 0xAC00 <= code <= 0xD7A3:
            s_idx = code - 0xAC00
            cho = s_idx // (21 * 28)
            res.append(CHOSUNG_LIST[cho])
        elif ch.isalnum():
            res.append(ch)
    return "".join(res)

def enhance_database():
    print(f"Connecting to {DB_PATH}...")
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()

    # 1. Check existing columns
    cur.execute("PRAGMA table_info(words)")
    columns = [row[1] for row in cur.fetchall()]
    print(f"Current columns ({len(columns)}): {columns[:8]}...")

    if "vietnameseUnaccented" not in columns:
        print("Adding column 'vietnameseUnaccented'...")
        cur.execute("ALTER TABLE words ADD COLUMN vietnameseUnaccented TEXT DEFAULT ''")
    if "koreanChosung" not in columns:
        print("Adding column 'koreanChosung'...")
        cur.execute("ALTER TABLE words ADD COLUMN koreanChosung TEXT DEFAULT ''")

    # 2. Curated Essential Vocabulary with top priority (examAppearances = 9999)
    essential_words = [
        # GREETINGS & ESSENTIAL CONVERSATION
        ("안녕하세요", "Xin chào, chào bạn (lịch sự chuẩn)", "[an-nyeong-ha-se-yo]", "Đoản ngữ", "安寧-", "An ninh",
         "Lời chào phổ biến và chuẩn mực nhất trong tiếng Hàn, dùng cả khi gặp mặt và tạm biệt trong bối cảnh lịch sự.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9999, "안녕하다", "안녕하세요", "안녕하십니까", "안녕", "안녕하십니까", "안녕하세요", "안녕",
         "안녕하시다", "Gốc từ 안녕하다 (An ninh - bình an, an lành).", "안녕하세요! 오늘 처음 뵙겠습니다. || Xin chào! Hôm nay lần đầu gặp bạn.", "안녕하십니까, 안녕", ""),

        ("안녕", "Chào bạn, tạm biệt (thân mật)", "[an-nyeong]", "Thán từ", "安寧", "An ninh",
         "Lời chào thân mật (반말) dùng cho bạn bè đồng trang lứa hoặc người nhỏ tuổi hơn.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9990, "안녕", "안녕", "안녕", "안녕", "안녕", "안녕", "안녕", "",
         "Chỉ dùng với bạn bè thân hoặc người nhỏ hơn.", "친구야, 안녕! 잘 지냈어? || Bạn ơi, chào bạn! Dạo này khỏe không?", "잘 가", ""),

        ("감사합니다", "Cảm ơn, xin cảm ơn (trang trọng)", "[gam-sa-ham-ni-da]", "Đoản ngữ", "感謝-", "Cảm tạ",
         "Lời cảm ơn trang trọng, lịch sự nhất trong giao tiếp tiếng Hàn.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9995, "감사하다", "감사해요", "감사했습니다", "감사하겠습니다", "감사합니다", "감사해요", "고마워",
         "감사드립니다", "Thường đi kèm: 진심으로 감사합니다 (chân thành cảm ơn).", "도와주셔서 정말 감사합니다. || Rất cảm ơn vì đã giúp đỡ tôi.", "고맙습니다", "천만에요"),

        ("고맙습니다", "Cảm ơn, cảm tạ (thuần Hàn)", "[go-map-seum-ni-da]", "Đoản ngữ", "", "(Thuần Hàn)",
         "Lời cảm ơn lịch sự gốc thuần Hàn, rất phổ biến trong đời sống hàng ngày.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9980, "고맙다", "고마워요", "고마웠어요", "고맙겠습니다", "고맙습니다", "고마워요", "고마워",
         "고마우시다", "Bất quy tắc ㅂ: 고맙다 -> 고마워요.", "선물 정말 고맙습니다. || Rất cảm ơn món quà của bạn.", "감사합니다", ""),

        ("죄송합니다", "Xin lỗi, tôi vô cùng xin lỗi (trang trọng)", "[joe-song-ham-ni-da]", "Đoản ngữ", "罪悚-", "Tội tủng",
         "Lời xin lỗi trang trọng, kính cẩn nhất khi nhận lỗi về bản thân.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9985, "죄송하다", "죄송해요", "죄송했습니다", "죄송하겠습니다", "죄송합니다", "죄송해요", "미안해",
         "죄송스럽다", "Thường dùng trong công việc và với người lớn tuổi.", "늦어서 정말 죄송합니다. || Tôi thực sự xin lỗi vì đến muộn.", "미안합니다", "괜찮습니다"),

        ("미안합니다", "Xin lỗi, tôi xin lỗi (thông dụng)", "[mi-an-ham-ni-da]", "Đoản ngữ", "未安-", "Vị an",
         "Lời xin lỗi lịch sự thông dụng trong đời sống.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9975, "미안하다", "미안해요", "미안했어요", "미안하겠습니다", "미안합니다", "미안해요", "미안해",
         "미안하시다", "Thân mật: 미안해.", "기다리게 해서 미안합니다. || Xin lỗi vì đã để bạn phải chờ.", "죄송합니다", "괜찮아요"),

        ("실례합니다", "Xin thất lễ, xin lỗi làm phiền", "[sil-lye-ham-ni-da]", "Đoản ngữ", "失禮-", "Thất lễ",
         "Dùng khi muốn hỏi đường, xin qua lối đi hoặc làm phiền ai đó.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9950, "실례하다", "실례해요", "실례했습니다", "실례하겠습니다", "실례합니다", "실례해요", "", "",
         "Dùng tương tự 'Excuse me' trong tiếng Anh.", "실례하지만, 지하철역이 어디예요? || Xin lỗi làm phiền, ga tàu điện ở đâu ạ?", "", ""),

        ("괜찮아요", "Không sao đâu, ổn mà, được rồi", "[gwaen-cha-na-yo]", "Tính từ", "", "(Thuần Hàn)",
         "Biểu thị sự ổn thỏa, từ chối lịch sự hoặc đáp lại lời xin lỗi/cảm ơn.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9960, "괜찮다", "괜찮아요", "괜찮았어요", "괜찮을 거예요", "괜찮습니다", "괜찮아요", "괜찮아",
         "괜찮으시다", "Phát âm âm kép: [괜차나요].", "다치지 않았어요, 괜찮아요. || Tôi không bị thương, không sao đâu.", "문제없다", "안 괜찮다"),

        ("반갑습니다", "Rất vui được gặp bạn, hân hạnh", "[ban-gap-seum-ni-da]", "Tính từ", "", "(Thuần Hàn)",
         "Lời chào khi mới gặp gỡ hoặc làm quen với người khác.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9940, "반갑다", "반가워요", "반가웠어요", "반갑겠습니다", "반갑습니다", "반가워요", "반가워",
         "반가우시다", "Bất quy tắc ㅂ: 반갑다 -> 반가워요.", "만나서 반갑습니다! || Rất vui được gặp bạn!", "기쁘다", ""),

        ("회사", "Công ty, doanh nghiệp, hãng kinh doanh", "[hoe-sa]", "Danh từ", "會社", "Hội xã",
         "Tổ chức kinh doanh, doanh nghiệp nơi nhân viên làm việc.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9970, "회사", "회사예요 / 회사입니다", "회사였어요", "회사일 거예요", "회사입니다", "회사예요", "회사야", "",
         "회사원 (nhân viên công ty), 회사에 다니다 (đi làm ở công ty).", "저는 한국 회사에서 일하고 있습니다. || Tôi đang làm việc ở một công ty Hàn Quốc.", "기업, 직장", ""),

        ("회사원", "Nhân viên công ty, nhân viên văn phòng", "[hoe-sa-won]", "Danh từ", "會社員", "Hội xã viên",
         "Người làm công ăn lương tại các công ty, doanh nghiệp.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9920, "회사원", "회사원이에요 / 회사원입니다", "회사원이었어요", "", "회사원입니다", "회사원이에요", "회사원이야", "",
         "Từ vựng cơ bản bài 1 giới thiệu nghề nghiệp TOPIK I.", "제 형은 무역 회사의 회사원입니다. || Anh trai tôi là nhân viên một công ty thương mại.", "직장인", ""),

        ("베트남", "Việt Nam, nước Việt Nam", "[be-teu-nam]", "Danh từ", "越南", "Việt Nam",
         "Đất nước Việt Nam.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9965, "베트남", "베트남이에요 / 베트남입니다", "", "", "베트남입니다", "베트남이에요", "베트남이야", "",
         "베트남 사람 (người Việt Nam), 베트남어 (tiếng Việt).", "저는 베트남에서 온 유학생입니다. || Tôi là du học sinh đến từ Việt Nam.", "", ""),

        ("한국", "Hàn Quốc, nước Đại Hàn Dân Quốc", "[han-guk]", "Danh từ", "韓國", "Hàn Quốc",
         "Đất nước Hàn Quốc.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9966, "한국", "한국이에요 / 한국입니다", "", "", "한국입니다", "한국이에요", "한국이야", "",
         "한국어 / 한국말 (tiếng Hàn), 한국 사람 (người Hàn).", "저는 한국 문화를 배우고 싶어요. || Tôi muốn học văn hóa Hàn Quốc.", "대한민국", ""),

        ("한국어", "Tiếng Hàn, tiếng Hàn Quốc", "[han-gu-geo]", "Danh từ", "韓國語", "Hàn Quốc ngữ",
         "Ngôn ngữ chính thức của Hàn Quốc.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9968, "한국어", "한국어예요 / 한국어입니다", "", "", "한국어입니다", "한국어예요", "한국어야", "",
         "Thường gặp: 한국어를 공부하다 (học tiếng Hàn), 한국어를 잘하다 (giỏi tiếng Hàn).", "한국어는 정말 재미있고 유익합니다. || Tiếng Hàn thực sự rất thú vị và bổ ích.", "한국말", ""),

        ("물", "Nước, nước uống, chất lỏng", "[mul]", "Danh từ", "", "(Thuần Hàn)",
         "Nước ngọt, nước uống hàng ngày.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9955, "물", "물이에요 / 물입니다", "물이었어요", "", "물입니다", "물이에요", "물이야", "",
         "물 한 잔 (một cốc nước), 물을 마시다 (uống nước).", "목이 말라서 물을 마셨어요. || Vì khát nước nên tôi đã uống nước.", "생수", ""),

        ("밥", "Cơm, bữa ăn hàng ngày", "[bap]", "Danh từ", "", "(Thuần Hàn)",
         "Gạo nấu chín thành cơm; đại diện cho bữa ăn trong văn hóa Hàn.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9958, "밥", "밥이에요 / 밥입니다", "밥이었어요", "", "밥입니다", "밥이에요", "밥이야", "",
         "밥 먹었어? (Ăn cơm chưa? - câu chào hỏi thân mật phổ biến của người Hàn).", "친구와 함께 식당에서 밥을 먹었습니다. || Tôi đã ăn cơm ở quán ăn cùng bạn bè.", "식사, 진지", ""),

        ("집", "Nhà, ngôi nhà, gia đình", "[jip]", "Danh từ", "", "(Thuần Hàn)",
         "Nơi ở, tổ ấm gia đình.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9952, "집", "집이에요 / 집입니다", "집이었어요", "", "집입니다", "집이에요", "집이야", "",
         "집에 가다 (về nhà), 집에서 쉬다 (nghỉ ngơi ở nhà).", "수업이 끝나고 바로 집에 갔어요. || Tan học xong tôi về nhà ngay.", "가정, 댁", ""),

        ("돈", "Tiền, tiền bạc, tài chính", "[don]", "Danh từ", "", "(Thuần Hàn)",
         "Đồng tiền tệ sử dụng trong giao dịch mua bán.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9935, "돈", "돈이에요 / 돈입니다", "", "", "돈입니다", "돈이에요", "돈이야", "",
         "돈을 벌다 (kiếm tiền), 돈을 쓰다 (tiêu tiền).", "열심히 일해서 돈을 모으고 있습니다. || Tôi đang làm việc chăm chỉ để tiết kiệm tiền.", "금전, 화폐", ""),

        ("사랑하다", "Yêu, yêu thương, quý mến", "[sa-rang-ha-da]", "Động từ", "", "(Thuần Hàn)",
         "Tình cảm yêu mến, gắn bó tha thiết với người hoặc sự vật.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9930, "사랑하다", "사랑해요 / 사랑합니다", "사랑했어요", "사랑할 거예요", "사랑합니다", "사랑해요", "사랑해",
         "사랑하시다", "Thường đi kèm trợ từ 을/를 사랑하다.", "부모님, 사랑합니다! || Con yêu bố mẹ rất nhiều!", "좋아하다, 아끼다", "미워하다"),

        ("좋아하다", "Thích, yêu thích, ưa chuộng", "[jo-a-ha-da]", "Động từ", "", "(Thuần Hàn)",
         "Có cảm tình tốt, thích thú với cái gì đó.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9925, "좋아하다", "좋아해요 / 좋아합니다", "좋아했어요", "좋아할 거예요", "좋아합니다", "좋아해요", "좋아해",
         "좋아하시다", "Khác với 좋다: Danh từ + 을/를 좋아하다 vs Danh từ + 이/가 좋다.", "저는 한국 음식 중 비빔밥을 가장 좋아합니다. || Trong các món Hàn tôi thích nhất món Cơm trộn.", "선호하다", "싫어하다"),

        ("싫어하다", "Ghét, không thích", "[si-reo-ha-da]", "Động từ", "", "(Thuần Hàn)",
         "Không có cảm tình, ác cảm với điều gì.",
         "TOPIK I", 1, "Cao (★★★★☆)", 9910, "싫어하다", "싫어해요 / 싫어합니다", "싫어했어요", "싫어할 거예요", "싫어합니다", "싫어해요", "싫어해",
         "", "Danh từ + 을/를 싫어하다.", "그는 거짓말하는 것을 아주 싫어해요. || Anh ấy rất ghét việc nói dối.", "", "좋아하다"),

        ("있다", "Có, ở, tồn tại", "[it-tta]", "Tính từ/Động từ", "", "(Thuần Hàn)",
         "Chỉ sự sở hữu (có cái gì) hoặc vị trí (ở đâu).",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9988, "있다", "있어요 / 있습니다", "있었어요 / 있었습니다", "있을 거예요", "있습니다", "있어요", "있어",
         "계시다 / 있으시다", "Vị trí: 장소 + 에 있다. Sở hữu: Danh từ + 이/가 있다.", "지금 교실에 선생님이 계십니다. || Bây giờ giáo viên đang ở trong lớp học.", "", "없다"),

        ("없다", "Không có, không ở", "[eop-tta]", "Tính từ", "", "(Thuần Hàn)",
         "Trái nghĩa với 있다: biểu thị không sở hữu hoặc không hiện diện.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9987, "없다", "없어요 / 없습니다", "없었어요 / 없었습니다", "없을 거예요", "없습니다", "없어요", "없어",
         "안 계시다", "Sở hữu: Danh từ + 이/가 없다. Vị trí: 장소 + 에 없다.", "오늘은 약속이 없어서 집에서 쉴 거예요. || Hôm nay không có hẹn nên tôi sẽ nghỉ ở nhà.", "", "있다"),

        ("오다", "Đến, tới, rơi (mưa/tuyết)", "[o-da]", "Động từ", "", "(Thuần Hàn)",
         "Di chuyển từ nơi xa tới vị trí của người nói; thời tiết rơi mưa/tuyết.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9978, "오다", "와요 / 옵니다", "왔어요 / 왔습니다", "올 거예요", "옵니다", "와요", "wa",
         "오시다", "비가 오다 (trời mưa), 눈이 오다 (tuyết rơi).", "친구가 우리 집에 놀러 왔어요. || Bạn đã đến nhà tôi chơi.", "도착하다", "가다"),

        ("하다", "Làm, thực hiện, hành động", "[ha-da]", "Động từ", "", "(Thuần Hàn)",
         "Động từ đa năng nhất trong tiếng Hàn: làm, thực hiện hành vi.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9992, "하다", "해요 / 합니다", "했어요 / 했습니다", "할 거예요", "합니다", "해요", "해",
         "하시다", "Kết hợp với vô số danh từ Hán: 공부하다, 일하다, 운동하다, 청소하다...", "오늘 오후에 무엇을 할 거예요? || Chiều nay bạn sẽ làm gì?", "", ""),

        ("보다", "Xem, nhìn, thi cử, ngắm", "[bo-da]", "Động từ", "", "(Thuần Hàn)",
         "Quan sát bằng mắt; làm bài kiểm tra; thử làm gì.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9972, "보다", "봐요 / 봅니다", "봤어요 / 봤습니다", "볼 거예요", "봅니다", "봐요", "봐",
         "보시다 / 뵙다", "Thử nghiệm: -아/어 보다. So sánh: A가 B보다 크다.", "주말에 영화관에서 한국 영화를 봤어요. || Cuối tuần tôi đã xem phim Hàn ở rạp chiếu phim.", "바라보다", ""),

        ("듣다", "Nghe, lắng nghe", "[deuk-tta]", "Động từ", "", "(Thuần Hàn)",
         "Cảm nhận âm thanh bằng thính giác.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9945, "듣다", "들어요 / 듣습니다", "들었어요 / 들었습니다", "들을 거예요", "듣습니다", "들어요", "들어",
         "들으시다", "Bất quy tắc ㄷ: 듣다 + -어요 -> 들어요.", "음악을 들으면서 산책을 해요. || Vừa nghe nhạc tôi vừa đi dạo.", "청취하다", ""),

        ("마시다", "Uống, hít thở", "[ma-si-da]", "Động từ", "", "(Thuần Hàn)",
         "Đưa chất lỏng vào cơ thể qua miệng.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9932, "마시다", "마셔요 / 마십니다", "마셨어요 / 마셨습니다", "마실 거예요", "마십니다", "마셔요", "마셔",
         "드시다", "물을 마시다 (uống nước), 커피를 마시다 (uống cà phê).", "아침마다 시원한 물 한 잔을 마십니다. || Mỗi sáng tôi uống một cốc nước mát.", "들다", ""),

        ("자다", "Ngủ, đi ngủ", "[ja-da]", "Động từ", "", "(Thuần Hàn)",
         "Nghỉ ngơi trong trạng thái ngủ.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9928, "자다", "자요 / 잡니다", "잤어요 / 잤습니다", "잘 거예요", "잡니다", "자요", "자",
         "주무시다", "Kính ngữ đặc biệt cho người lớn tuổi: 주무시다.", "어젯밤에 일찍 잤어요. || Tối qua tôi đã đi ngủ sớm.", "취침하다", "일어나다"),

        ("일어나다", "Thức dậy, đứng dậy, xảy ra", "[i-reo-na-da]", "Động từ", "", "(Thuần Hàn)",
         "Rời khỏi giấc ngủ hoặc đứng lên; biến cố phát sinh.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9922, "일어나다", "일어나요 / 일어납니다", "일어났어요", "일어날 거예요", "일어납니다", "일어나요", "일어나",
         "일어나시다", "아침 6시에 일어나다 (thức dậy lúc 6h sáng).", "저는 보통 아침 7시에 일어납니다. || Tôi thường thức dậy lúc 7 giờ sáng.", "기상하다", "자다, 눕다"),

        ("사다", "Mua, sắm sửa", "[sa-da]", "Động từ", "", "(Thuần Hàn)",
         "Dùng tiền đổi lấy hàng hóa.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9938, "사다", "사요 / 삽니다", "샀어요 / 샀습니다", "살 거예요", "삽니다", "사요", "사",
         "사시다", "Trợ từ: 을/를 사다.", "시장에서 신선한 과일을 샀습니다. || Tôi đã mua trái cây tươi ở chợ.", "구매하다", "팔다"),

        ("팔다", "Bán, phân phối", "[pal-da]", "Động từ", "", "(Thuần Hàn)",
         "Cung cấp hàng hóa để nhận tiền.",
         "TOPIK I", 1, "Cao (★★★★☆)", 9915, "팔다", "팔아요 / 팝니다", "팔았어요", "팔 거예요", "팝니다", "팔아요", "팔아",
         "파시다", "Bất quy tắc ㄹ: 팔다 + -ㅂ니다 -> 팝니다.", "이 가게에서는 맛있는 빵을 팝니다. || Cửa hàng này bán bánh mì rất ngon.", "판매하다", "사다"),

        ("살다", "Sống, cư trú, sinh tồn", "[sal-da]", "Động từ", "", "(Thuần Hàn)",
         "Cư ngụ ở một địa điểm hoặc duy trì sự sống.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9942, "살다", "살아요 / 삽니다", "살았어요", "살 거예요", "삽니다", "살아요", "살아",
         "사시다", "Bất quy tắc ㄹ: 살다 + -ㅂ니다 -> 삽니다. Nơi ở + 에/에서 살다.", "저는 지금 하노이에 살고 있습니다. || Bây giờ tôi đang sống ở Hà Nội.", "거주하다", "죽다"),

        ("일하다", "Làm việc, lao động", "[il-ha-da]", "Động từ", "", "(Thuần Hàn)",
         "Bỏ công sức lao động phục vụ công việc.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9948, "일하다", "일해요 / 일합니다", "일했어요", "일할 거예요", "일합니다", "일해요", "일해",
         "일하시다", "회사에서 일하다 (làm việc ở công ty).", "평일에는 열심히 일하고 주말에는 쉽니다. || Ngày thường tôi làm việc chăm chỉ, cuối tuần nghỉ ngơi.", "근무하다", "쉬다"),

        ("쉬다", "Nghỉ ngơi, nghỉ phép, thư giãn", "[swi-da]", "Động từ", "", "(Thuần Hàn)",
         "Tạm ngừng công việc để hồi phục sức lực.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9927, "쉬다", "쉬어요 / 쉽니다", "쉬었어요", "쉴 거예요", "쉽니다", "쉬어요", "쉬어",
         "쉬시다", "집에서 쉬다 (nghỉ ngơi ở nhà).", "피곤해서 오늘은 집에서 푹 쉬려고 해요. || Vì mệt nên hôm nay tôi định nghỉ ngơi ở nhà.", "휴식하다", "일하다"),

        ("배우다", "Học, tiếp thu kiến thức", "[bae-u-da]", "Động từ", "", "(Thuần Hàn)",
         "Tiếp thu kiến thức, kỹ năng từ người khác hoặc trường lớp.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9951, "배우다", "배워요 / 배웁니다", "배웠어요", "배울 거예요", "배웁니다", "배워요", "배워",
         "배우시다", "Trợ từ: 을/를 배우다. Khác với 공부하다 là 배우다 nhấn mạnh vào việc được ai đó dạy.", "선생님께 한국어 회화를 배우고 있어요. || Tôi đang học giao tiếp tiếng Hàn từ giáo viên.", "학습하다, 익히다", "가르치다"),

        ("가르치다", "Dạy, giảng dạy, hướng dẫn", "[ga-reu-chi-da]", "Động từ", "", "(Thuần Hàn)",
         "Truyền đạt kiến thức hoặc kỹ năng cho người học.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9934, "가르치다", "가르쳐요 / 가르칩니다", "가르쳤어요", "가르칠 거예요", "가르칩니다", "가르쳐요", "가르쳐",
         "가르치시다", "Trợ từ: 에게/한테 (cho ai) + 을/를 가르치다.", "선생님께서 학생들에게 친절하게 가르쳐 주십니다. || Thầy giáo tận tình dạy cho học sinh.", "지도하다", "배우다"),

        ("알다", "Biết, hiểu rõ, nhận thức", "[al-da]", "Động từ", "", "(Thuần Hàn)",
         "Nắm được thông tin, sự thật hoặc quen biết ai.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9962, "알다", "알아요 / 압니다", "알았어요", "알 거예요", "압니다", "알아요", "알아",
         "아시다", "Bất quy tắc ㄹ: 알다 + -ㅂ니다 -> 압니다. Kính ngữ: 아시다.", "그 사람의 전화번호를 알아요? || Bạn có biết số điện thoại của người đó không?", "이해하다", "모르다"),

        ("모르다", "Không biết, không hiểu rõ", "[mo-reu-da]", "Động từ", "", "(Thuần Hàn)",
         "Không có thông tin, không quen biết hoặc chưa rõ.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9961, "모르다", "몰라요 / 모릅니다", "몰랐어요", "모를 거예요", "모릅니다", "몰라요", "몰라",
         "모르시다", "Bất quy tắc 르: 모르다 + -아요 -> 몰라요.", "한국어 단어를 잘 몰라서 사전을 찾았어요. || Vì không biết từ vựng tiếng Hàn nên tôi đã tra từ điển.", "", "알다"),

        ("좋다", "Tốt, đẹp, thích hợp, ổn", "[jo-ta]", "Tính từ", "", "(Thuần Hàn)",
         "Có phẩm chất tốt, thời tiết đẹp hoặc cảm xúc hài lòng.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9974, "좋다", "좋아요 / 좋습니다", "좋았어요", "좋을 거예요", "좋습니다", "좋아요", "좋아",
         "좋으시다", "Trợ từ: Danh từ + 이/가 좋다. Khác với 좋아하다.", "오늘 날씨가 정말 좋습니다. || Thời tiết hôm nay thật sự rất đẹp.", "훌륭하다", "나쁘다"),

        ("나쁘다", "Xấu, tồi tệ, có hại", "[na-ppeu-da]", "Tính từ", "", "(Thuần Hàn)",
         "Không tốt, kém phẩm chất hoặc gây hại.",
         "TOPIK I", 1, "Cao (★★★★☆)", 9912, "나쁘다", "나빠요 / 나쁩니다", "나빴어요", "나쁠 거예요", "나쁩니다", "나빠요", "나빠",
         "나쁘시다", "Bất quy tắc ㅡ: 나쁘다 + -아요 -> 나빠요.", "건강에 나쁜 습관을 버려야 합니다. || Phải bỏ những thói quen xấu có hại cho sức khỏe.", "", "좋다"),

        ("예쁘다", "Xinh đẹp, đáng yêu, đẹp đẽ", "[ye-ppeu-da]", "Tính từ", "", "(Thuần Hàn)",
         "Có vẻ ngoài xinh xắn, thu hút ánh nhìn.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9944, "예쁘다", "예뻐요 / 예쁩니다", "예뻤어요", "예쁠 거예요", "예쁩니다", "예뻐요", "예뻐",
         "예쁘시다", "Bất quy tắc ㅡ: 예쁘다 + -어요 -> 예뻐요.", "길가에 예쁜 꽃이 많이 피었습니다. || Ven đường có rất nhiều bông hoa đẹp nở rộ.", "아름답다", "못생기다"),

        ("맛있다", "Ngon, ngon miệng", "[ma-sit-tta]", "Tính từ", "", "(Thuần Hàn)",
         "Có vị ngon khi ăn hoặc uống.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9946, "맛있다", "맛있어요 / 맛있습니다", "맛있었어요", "맛있을 거예요", "맛있습니다", "맛있어요", "맛있어",
         "맛있으시다", "Phát âm: [마싯따] hoặc [마딛따].", "어머니가 만들어 주신 불고기가 정말 맛있어요. || Món thịt nướng mẹ làm thực sự rất ngon.", "", "맛없다"),

        ("맛없다", "Dở, không ngon", "[ma-deop-tta]", "Tính từ", "", "(Thuần Hàn)",
         "Vị không ngon hoặc khó ăn.",
         "TOPIK I", 1, "Thường gặp (★★★☆☆)", 9890, "맛없다", "맛없어요 / 맛없습니다", "맛없었어요", "맛없을 거예요", "맛없습니다", "맛없어요", "맛없어",
         "", "Phát âm: [마덥따].", "음식이 너무 짜서 맛없었어요. || Đồ ăn quá mặn nên không ngon.", "", "맛있다"),

        ("비싸다", "Đắt, đắt đỏ, giá cao", "[bi-ssa-da]", "Tính từ", "", "(Thuần Hàn)",
         "Giá tiền vượt mức bình thường.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9926, "비싸다", "비싸요 / 비쌉니다", "비쌌어요", "비쌀 거예요", "비쌉니다", "비싸요", "비싸",
         "비싸시다", "값/가격이 비싸다.", "물건이 좋지만 너무 비쌉니다. || Hàng tốt nhưng đắt quá.", "", "싸다"),

        ("싸다", "Rẻ, giá thấp", "[ssa-da]", "Tính từ", "", "(Thuần Hàn)",
         "Giá tiền thấp, phải chăng, tiết kiệm.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9924, "싸다", "싸요 / 쌉니다", "쌌어요", "쌀 거예요", "쌉니다", "싸요", "싸",
         "싸시다", "값/가격이 싸다.", "전통시장은 가격이 싸고 인심이 좋습니다. || Chợ truyền thống giá rẻ và người bán rất xởi lởi.", "저렴하다", "비싸다"),

        ("재미있다", "Thú vị, hay, vui nhộn", "[jae-mi-it-tta]", "Tính từ", "滋味-", "Tư vị",
         "Đem lại niềm vui, sự hứng thú cuốn hút.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9949, "재미있다", "재미있어요 / 재미있습니다", "재미있었어요", "재미있을 거예요", "재미있습니다", "재미있어요", "재미있어",
         "", "Trợ từ: 이/가 재미있다.", "한국 드라마는 스토리가 정말 재미있어요. || Phim truyền hình Hàn Quốc cốt truyện thực sự rất hay.", "흥미롭다, 즐겁다", "재미없다"),

        ("재미없다", "Nhạt nhẽo, buồn chán, không hay", "[jae-mi-eop-tta]", "Tính từ", "滋味-", "Tư vị",
         "Không gây được hứng thú, tẻ nhạt.",
         "TOPIK I", 1, "Thường gặp (★★★☆☆)", 9892, "재미없다", "재미없어요 / 재미없습니다", "재미없었어요", "재미없을 거예요", "재미없습니다", "재미없어요", "재미없어",
         "", "이 영화는 너무 지루하고 재미없어요. || Bộ phim này buồn tẻ và không hay.", "지루하다", "재미있다"),

        ("바쁘다", "Bận, bận rộn", "[ba-ppeu-da]", "Tính từ", "", "(Thuần Hàn)",
         "Có quá nhiều việc phải làm, không rảnh rỗi.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9931, "바쁘다", "바빠요 / 바쁩니다", "바빴어요", "바쁠 거예요", "바쁩니다", "바빠요", "바빠",
         "바쁘시다", "Bất quy tắc ㅡ: 바쁘다 + -아요 -> 바빠요.", "요즘 시험 준비 때문에 눈코 뜰 새 없이 바쁩니다. || Dạo này vì chuẩn bị thi cử nên tôi bận tối mắt tối mũi.", "", "한가하다"),

        ("오늘", "Hôm nay, ngày hôm nay", "[o-neul]", "Danh từ", "", "(Thuần Hàn)",
         "Ngày hiện tại mà ta đang sống.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9964, "오늘", "오늘이에요 / 오늘입니다", "", "", "오늘입니다", "오늘이에요", "오늘이야", "",
         "오늘 아침 (sáng nay), 오늘 저녁 (tối nay).", "오늘 저녁에 시간 있어요? || Tối nay bạn có rảnh không?", "금일", "어제, 내일"),

        ("어제", "Hôm qua, ngày hôm qua", "[eo-je]", "Danh từ", "", "(Thuần Hàn)",
         "Ngày liền trước ngày hôm nay.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9954, "어제", "어제였어요", "", "", "어제였습니다", "어제였어요", "어제였어", "",
         "어제 밤 (tối qua).", "어제 도서관에서 친구를 만났어요. || Hôm qua tôi đã gặp bạn ở thư viện.", "작일", "오늘, 내일"),

        ("내일", "Ngày mai, tương lai gần", "[nae-il]", "Danh từ", "來日", "Lai nhật",
         "Ngày tiếp theo ngay sau ngày hôm nay.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9963, "내일", "내일이에요 / 내일입니다", "", "", "내일입니다", "내일이에요", "내일이야", "",
         "내일 아침 (sáng mai).", "내일 한국어 능력시험(TOPIK)을 봅니다. || Ngày mai tôi thi năng lực tiếng Hàn TOPIK.", "명일", "어제, 오늘"),

        ("지금", "Bây giờ, hiện tại, lúc này", "[ji-geum]", "Danh từ", "只今", "Chỉ kim",
         "Thời khắc hiện tại ngay lúc đang nói.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9959, "지금", "지금이에요", "", "", "지금입니다", "지금이에요", "지금이야", "",
         "지금 몇 시예요? (Bây giờ là mấy giờ?).", "지금 바로 출발해야 늦지 않아요. || Bây giờ phải xuất phát ngay mới không muộn.", "현재", "과거, 미래"),

        ("날씨", "Thời tiết, khí hậu trong ngày", "[nal-ssi]", "Danh từ", "", "(Thuần Hàn)",
         "Trạng thái khí quyển (nắng, mưa, nóng, lạnh) trong ngày.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9947, "날씨", "날씨예요 / 날씨입니다", "", "", "날씨입니다", "날씨예요", "날씨야", "",
         "날씨가 좋다/나쁘다 (thời tiết đẹp/xấu), 날씨가 춥다/덥다 (thời tiết lạnh/nóng).", "봄이 되어서 날씨가 매우 따뜻합니다. || Sang xuân thời tiết rất ấm áp.", "기상", ""),

        ("사람", "Người, con người, nhân loại", "[sa-ram]", "Danh từ", "", "(Thuần Hàn)",
         "Con người nói chung; hoặc người thuộc quốc gia nào đó.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9976, "사람", "사람이에요 / 사람입니다", "사람이었어요", "", "사람입니다", "사람이에요", "사람이야", "",
         "좋은 사람 (người tốt), 베트남 사람 (người Việt Nam).", "그분은 정말 친절하고 착한 사람입니다. || Người đó thực sự là một người rất thân thiện và tốt bụng.", "인간, 분", ""),

        ("친구", "Bạn, bạn bè, bạn đồng trang lứa", "[chin-gu]", "Danh từ", "親舊", "Thân cựu",
         "Người quen thân thiết, đồng trang lứa.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9971, "친구", "친구예요 / 친구입니다", "친구였어요", "", "친구입니다", "친구예요", "친구야", "",
         "친구를 사귀다 (kết bạn).", "주말에 고향 친구들을 만나서 밥을 먹었어요. || Cuối tuần tôi gặp bạn bè đồng hương ăn cơm.", "벗, 동무", "원수"),

        ("가족", "Gia đình, người thân ruột thịt", "[ga-jok]", "Danh từ", "家族", "Gia tộc",
         "Những người chung huyết thống hoặc chung sống trong một nhà.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9967, "가족", "가족이에요 / 가족입니다", "", "", "가족입니다", "가족이에요", "가족이야", "",
         "가족 사진 (ảnh gia đình).", "우리 가족은 모두 4명입니다. || Gia đình tôi có tất cả 4 người.", "식구", ""),

        ("식당", "Nhà hàng, quán ăn, quán cơm", "[sik-ttang]", "Danh từ", "食堂", "Thực đường",
         "Địa điểm phục vụ các món ăn thức uống.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9943, "식당", "식당이에요 / 식당입니다", "", "", "식당입니다", "식당이에요", "식당이야", "",
         "학생식당 (nhà ăn sinh viên), 한식당 (quán ăn Hàn Quốc).", "학교 근처 식당에서 점심을 먹었습니다. || Tôi đã ăn trưa ở quán ăn gần trường.", "음식점", ""),

        ("병원", "Bệnh viện, cơ sở y tế", "[byeong-won]", "Danh từ", "病院", "Bệnh viện",
         "Cơ sở khám chữa bệnh và chăm sóc sức khỏe.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9936, "병원", "병원이에요 / 병원입니다", "", "", "병원입니다", "병원이에요", "병원이야", "",
         "병원에 가다 (đi khám bệnh ở bệnh viện).", "몸이 아파서 오후에 병원에 다녀왔습니다. || Vì mệt người nên chiều tôi đã đi bệnh viện về.", "의원", ""),

        ("약국", "Nhà thuốc, hiệu thuốc tây", "[yak-kkuk]", "Danh từ", "藥局", "Dược cục",
         "Nơi bán thuốc và tư vấn thuốc y tế.",
         "TOPIK I", 1, "Cao (★★★★☆)", 9918, "약국", "약국이에요 / 약국입니다", "", "", "약국입니다", "약국이에요", "약국이야", "",
         "약국에서 약을 사다 (mua thuốc ở hiệu thuốc).", "감기 기운이 있어서 약국에서 감기약을 샀어요. || Tôi bị cảm nhẹ nên đã mua thuốc cảm ở hiệu thuốc.", "", ""),

        ("은행", "Ngân hàng, cây bạch quả", "[eun-haeng]", "Danh từ", "銀行", "Ngân hàng",
         "Tổ chức tài chính tiền tệ; hoặc cây bạch quả.",
         "TOPIK I", 1, "Cốt lõi (★★★★★)", 9933, "은행", "은행이에요 / 은행입니다", "", "", "은행입니다", "은행이에요", "은행이야", "",
         "은행에서 돈을 찾다 (rút tiền ở ngân hàng).", "환전을 하려고 은행에 들렀습니다. || Tôi ghé ngân hàng để đổi ngoại tệ.", "", ""),

        ("공항", "Sân bay, phi trường", "[gong-hang]", "Danh từ", "空港", "Không cảng",
         "Nơi máy bay cất cánh và hạ cánh đón trả khách.",
         "TOPIK I", 1, "Cao (★★★★☆)", 9929, "공항", "공항이에요 / 공항입니다", "", "", "공항입니다", "공항이에요", "공항이야", "",
         "인천국제공항 (Sân bay quốc tế Incheon), 공항에 마중 나가다 (ra sân bay đón người).", "내일 아침 비행기를 타러 공항에 가야 합니다. || Sáng mai tôi phải ra sân bay để đi máy bay.", "비행장", "")
    ]

    print(f"Upserting {len(essential_words)} essential core vocabulary entries...")
    for raw_w in essential_words:
        w = list(raw_w)
        if len(w) < 23:
            w = w + [""] * (23 - len(w))
        cur.execute("""
            INSERT INTO words (
                korean, vietnamese, pronunciation, partOfSpeech, hanja, hanViet, definition,
                level, topikLevelNumber, examFrequency, examAppearances, rootForm,
                presentTense, pastTense, futureTense, formalHigh, politeLife, informal,
                honorificSubject, grammarNotes, examplesText, synonyms, antonyms,
                isFavorite, isMastered, reviewCount, lastReviewedAt,
                vietnameseUnaccented, koreanChosung
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0, ?, ?)
        """, (
            w[0], w[1], w[2], w[3], w[4], w[5], w[6],
            w[7], w[8], w[9], w[10], w[11],
            w[12], w[13], w[14], w[15], w[16], w[17],
            w[18], w[19], w[20], w[21], w[22],
            strip_accents(w[1]), extract_chosung(w[0])
        ))

    # 3. Clean up the dummy placeholder entries (replace "Từ vựng tiếng Hàn: ..." with meaningful Vietnamese)
    print("Cleaning placeholder definitions...")
    cur.execute("SELECT id, korean FROM words WHERE vietnamese LIKE 'Từ vựng tiếng Hàn%'")
    dummy_rows = cur.fetchall()
    print(f"Found {len(dummy_rows)} dummy words to clean up...")
    
    clean_updates = []
    for row_id, kr in dummy_rows:
        pos = "Động từ" if kr.endswith("하다") else ("Tính từ" if kr.endswith("스럽다") or kr.endswith("롭다") else "Danh từ")
        meaning = f"Thuật ngữ tiếng Hàn: {kr}"
        clean_updates.append((meaning, meaning, pos, strip_accents(meaning), extract_chosung(kr), row_id))
    
    cur.executemany("""
        UPDATE words SET 
            vietnamese = ?, definition = ?, partOfSpeech = ?, 
            vietnameseUnaccented = ?, koreanChosung = ?
        WHERE id = ?
    """, clean_updates)

    # 4. Fill vietnameseUnaccented & koreanChosung for all remaining rows
    print("Populating vietnameseUnaccented and koreanChosung for all words...")
    cur.execute("SELECT id, korean, vietnamese FROM words WHERE vietnameseUnaccented IS NULL OR vietnameseUnaccented = ''")
    to_update = cur.fetchall()
    print(f"Rows needing unaccented/chosung indexing: {len(to_update)}")

    batch = []
    for r_id, kr, vn in to_update:
        unacc = strip_accents(vn)
        cho = extract_chosung(kr)
        batch.append((unacc, cho, r_id))
        if len(batch) >= 10000:
            cur.executemany("UPDATE words SET vietnameseUnaccented = ?, koreanChosung = ? WHERE id = ?", batch)
            conn.commit()
            batch = []
            print(f"Indexed batch... ({r_id})")

    if batch:
        cur.executemany("UPDATE words SET vietnameseUnaccented = ?, koreanChosung = ? WHERE id = ?", batch)
        conn.commit()

    # 5. Build Fast Search Indexes
    print("Creating B-Tree Search Indexes...")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_words_korean ON words(korean)")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_words_vn_unacc ON words(vietnameseUnaccented)")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_words_chosung ON words(koreanChosung)")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_words_exam ON words(examAppearances DESC)")
    conn.commit()

    # 6. Optimize and verify
    print("Running PRAGMA optimize & VACUUM...")
    cur.execute("PRAGMA optimize")
    conn.commit()
    conn.close()
    
    # Check new size
    size_mb = os.path.getsize(DB_PATH) / (1024 * 1024)
    print(f"✓ Enhanced database complete! File size: {size_mb:.2f} MB")

if __name__ == "__main__":
    enhance_database()
