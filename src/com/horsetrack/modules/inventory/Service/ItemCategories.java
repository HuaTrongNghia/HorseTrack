package com.horsetrack.equinecare.modules.inventory.service;

import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Định nghĩa nhóm vật tư dùng chung cho kho, phác đồ, yêu cầu vật tư và khẩu phần.
 * Dữ liệu trong Inventory_Items.category lưu nhãn tiếng Việt ("Thuốc", "Thức ăn"...); một số bản ghi cũ lưu mã tiếng Anh
 * ("MEDICINE", "FEED"...). Mọi so sánh nhóm vật tư đi qua lớp này để nhận cả hai dạng.
 */
public final class ItemCategories {

    /** Mã tiếng Anh → nhãn tiếng Việt (dạng chuẩn được lưu khi tạo / sửa vật tư) */
    public static final Map<String, String> LABELS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("FEED", "Thức ăn");
        m.put("SUPPLEMENT", "Thức ăn bổ sung");
        m.put("MEDICINE", "Thuốc");
        m.put("VACCINE", "Vaccine");
        m.put("MEDICAL_SUPPLY", "Vật tư y tế");
        m.put("STABLE_SUPPLY", "Vật tư chuồng trại");
        m.put("EQUIPMENT", "Dụng cụ");
        m.put("OTHER", "Khác");
        LABELS = java.util.Collections.unmodifiableMap(m);
    }

    private static final Set<String> MEDICAL_CODES = Set.of("MEDICINE", "VACCINE", "MEDICAL_SUPPLY");
    private static final Set<String> FEED_CODES = Set.of("FEED", "SUPPLEMENT");

    /** Mọi cách ghi của nhóm thuốc / vaccine / vật tư y tế (dùng cho truy vấn IN / NOT IN) */
    public static final List<String> MEDICAL_VALUES = valuesOf(MEDICAL_CODES, "Thuoc", "Vat tu y te");

    private ItemCategories() {
    }

    /** Đổi về mã tiếng Anh (FEED, MEDICINE...) từ mã hoặc nhãn; không nhận ra thì trả null */
    public static String code(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String c = category.trim();
        String upper = c.toUpperCase(Locale.ROOT);
        if (LABELS.containsKey(upper)) {
            return upper;
        }
        String plain = strip(c);
        for (Map.Entry<String, String> e : LABELS.entrySet()) {
            if (strip(e.getValue()).equals(plain)) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Nhãn tiếng Việt chuẩn để lưu; không nhận ra thì giữ nguyên giá trị (đã trim) */
    public static String label(String category) {
        String code = code(category);
        return code != null ? LABELS.get(code) : (category == null ? null : category.trim());
    }

    /** Thuốc, vaccine, vật tư y tế: chỉ đi theo phác đồ của bác sĩ, Groom không được yêu cầu */
    public static boolean isMedical(String category) {
        return MEDICAL_CODES.contains(code(category));
    }

    public static boolean isFeedCategory(String category) {
        return FEED_CODES.contains(code(category));
    }

    /**
     * Món được dùng trong khẩu phần: nhóm thức ăn / thức ăn bổ sung, hoặc sản phẩm bổ sung - tiêu hóa đường uống
     * không cần kê đơn (ví dụ vitamin uống). Thuốc cần kê đơn, thuốc tiêm / bôi không được đưa vào khẩu phần.
     */
    public static boolean isFeedItem(InventoryItem item) {
        if (item == null) {
            return false;
        }
        if (isFeedCategory(item.getCategory())) {
            return true;
        }
        String group = item.getItemGroup() == null ? "" : item.getItemGroup().toLowerCase(Locale.ROOT);
        String route = item.getRoute() == null ? "" : item.getRoute().toLowerCase(Locale.ROOT);
        boolean oral = route.isEmpty() || route.contains("uống");
        boolean supplement = group.contains("bổ sung") || group.contains("tiêu hóa");
        return oral && supplement && !Boolean.TRUE.equals(item.getRequiresPrescription());
    }

    private static List<String> valuesOf(Set<String> codes, String... extra) {
        List<String> values = codes.stream().flatMap(c -> java.util.stream.Stream.of(c, LABELS.get(c))).collect(Collectors.toList());
        values.addAll(List.of(extra));
        return List.copyOf(values);
    }

    // So sánh không dấu, không phân biệt hoa thường
    private static String strip(String s) {
        String n = java.text.Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return n.replace('đ', 'd');
    }
}
