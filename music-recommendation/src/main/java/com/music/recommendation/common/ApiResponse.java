package com.music.recommendation.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 統一 REST API 響應封裝
 *
 * @param <T> data 字段的類型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /** 業務碼：0 表示成功，非 0 表示各種錯誤 */
    private int code;

    /** 響應消息 */
    private String msg;

    /** 響應數據 */
    private T data;

    /** 服務端時間戳 */
    private long timestamp = System.currentTimeMillis();

    // ==================== 工廠方法 ====================

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> error(int code, String msg) {
        return new ApiResponse<>(code, msg, null, System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> notFound(String msg) {
        return error(404, msg);
    }

    public static <T> ApiResponse<T> badRequest(String msg) {
        return error(400, msg);
    }

    public static <T> ApiResponse<T> unauthorized(String msg) {
        return error(401, msg);
    }
}
