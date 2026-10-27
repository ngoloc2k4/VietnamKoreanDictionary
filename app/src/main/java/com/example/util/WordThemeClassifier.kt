package com.example.util

import com.example.data.model.WordEntity

/**
 * Bộ phân loại từ vựng theo các chủ đề đời sống thiết thực:
 * 1. Đời sống & Gia đình
 * 2. Ẩm thực & Ăn uống
 * 3. Công sở & Việc làm
 * 4. Trường học & Thi cử
 * 5. Du lịch & Giao thông
 * 6. Mua sắm & Tiền bạc
 * 7. Sức khỏe & Y tế
 * 8. Cảm xúc & Tâm lý
 */
object WordThemeClassifier {

    private val DAILY_KEYWORDS = listOf(
        "gia đình", "nhà", "phòng", "mẹ", "bố", "cha", "ba", "anh", "em", "chị", "con",
        "sinh hoạt", "đời sống", "cuộc sống", "ngủ", "thức dậy", "tắm", "rửa", "giặt",
        "quần áo", "bạn bè", "hàng xóm", "người thân", "nhà vệ sinh", "bếp", "bàn", "ghế",
        "dọn dẹp", "quét dọn", "nấu nướng", "thức ăn", "nhà cửa"
    )

    private val FOOD_KEYWORDS = listOf(
        "ăn", "uống", "cơm", "món", "món ăn", "nấu", "thịt", "cá", "canh", "rau", "hoa quả",
        "trái cây", "quán", "nhà hàng", "bánh", "trà", "cà phê", "rượu", "bia", "ngon", "đói",
        "chua", "cay", "mặn", "ngọt", "nước ngọt", "ẩm thực", "bữa ăn", "sáng", "trưa", "tối",
        "gia vị", "chế biến", "hương vị", "thực đơn", "pha chế"
    )

    private val WORK_KEYWORDS = listOf(
        "công ty", "việc", "công việc", "văn phòng", "lương", "sếp", "giám đốc", "đồng nghiệp",
        "họp", "dự án", "hợp đồng", "phỏng vấn", "nghề", "nghề nghiệp", "kinh doanh", "khách hàng",
        "nhân viên", "làm việc", "tuyển dụng", "báo cáo", "chức vụ", "thăng tiến", "nghỉ việc",
        "kinh tế", "doanh nghiệp", "thương mại", "thành tích", "chuyên môn", "kinh nghiệm", "đồng lương"
    )

    private val EDUCATION_KEYWORDS = listOf(
        "học", "học tập", "trường", "trường học", "giáo viên", "thầy", "cô", "học sinh",
        "sinh viên", "sách", "vở", "thi", "kỳ thi", "bài tập", "lớp", "lớp học", "đại học",
        "nghiên cứu", "kiểm tra", "tốt nghiệp", "bài giảng", "học bổng", "điểm số", "giảng viên",
        "thư viện", "chuyên ngành", "khoa học", "tri thức", "giáo dục"
    )

    private val TRAVEL_KEYWORDS = listOf(
        "du lịch", "đi lại", "máy bay", "xe buýt", "tàu", "tàu hỏa", "tàu điện", "ga",
        "sân bay", "khách sạn", "vé", "đường", "xe hơi", "ô tô", "hành lý", "tham quan",
        "nghỉ dưỡng", "chuyến đi", "bản đồ", "điểm đến", "giao thông", "hộ chiếu", "visa",
        "đặt phòng", "phong cảnh", "hành trình"
    )

    private val SHOPPING_KEYWORDS = listOf(
        "mua", "bán", "tiền", "giá", "chợ", "siêu thị", "thẻ", "thanh toán", "đắt", "rẻ",
        "giảm giá", "cửa hàng", "mua sắm", "chi tiêu", "tiết kiệm", "hóa đơn", "sản phẩm",
        "đặt hàng", "tiền mặt", "ngân hàng", "ví", "khuyến mãi", "trả giá", "mặc cả"
    )

    private val HEALTH_KEYWORDS = listOf(
        "bệnh", "bác sĩ", "bệnh viện", "thuốc", "đau", "sốt", "khám", "sức khỏe", "y tế",
        "y tá", "cơ thể", "mệt mỏi", "chữa", "điều trị", "phòng khám", "tiêm", "nha khoa",
        "cảm cúm", "dược", "tai nạn", "cấp cứu", "khỏe", "ốm", "dị ứng"
    )

    private val EMOTION_KEYWORDS = listOf(
        "vui", "buồn", "giận", "yêu", "ghét", "hạnh phúc", "lo lắng", "sợ", "nhớ", "mệt",
        "cười", "khóc", "bất ngờ", "tự hào", "cảm xúc", "thất vọng", "tâm trạng", "cô đơn",
        "hồi hộp", "thích", "tức giận", "xúc động", "tuyệt vọng", "hy vọng", "ghen tị"
    )

    fun matchesDaily(word: WordEntity): Boolean = matchesAny(word, DAILY_KEYWORDS)
    fun matchesFood(word: WordEntity): Boolean = matchesAny(word, FOOD_KEYWORDS)
    fun matchesWork(word: WordEntity): Boolean = matchesAny(word, WORK_KEYWORDS)
    fun matchesEducation(word: WordEntity): Boolean = matchesAny(word, EDUCATION_KEYWORDS)
    fun matchesTravel(word: WordEntity): Boolean = matchesAny(word, TRAVEL_KEYWORDS)
    fun matchesShopping(word: WordEntity): Boolean = matchesAny(word, SHOPPING_KEYWORDS)
    fun matchesHealth(word: WordEntity): Boolean = matchesAny(word, HEALTH_KEYWORDS)
    fun matchesEmotion(word: WordEntity): Boolean = matchesAny(word, EMOTION_KEYWORDS)

    private fun matchesAny(word: WordEntity, keywords: List<String>): Boolean {
        val target = (word.vietnamese + " " + word.definition).lowercase()
        return keywords.any { target.contains(it) }
    }
}
