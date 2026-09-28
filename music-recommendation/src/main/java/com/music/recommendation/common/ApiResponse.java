package com.music.recommendation.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
    private long timestamp;

    /** 顯式全參構造（Lombok + 手動 javac 雙保險） */
    public ApiResponse(int code, String msg, T data) {
        this(code, msg, data, System.currentTimeMillis());
    }

    public ApiResponse(int code, String msg, T data, long timestamp) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.timestamp = timestamp;
    }

    // ==================== 工廠方法（返回 ApiResponse 本身，HTTP status 由調用方決定） ====================

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

    public static <T> ApiResponse<T> forbidden(String msg) {
        return error(403, msg);
    }

    // ==================== HTTP Status 響應（用於 @RestController 返回 ResponseEntity） ====================

    public ResponseEntity<ApiResponse<T>> toResponse(HttpStatus status) {
        return ResponseEntity.status(status).body(this);
    }

    public static <T> ResponseEntity<ApiResponse<T>> okEntity(T data) {
        return ResponseEntity.ok(ok(data));
    }

    public static <T> ResponseEntity<ApiResponse<T>> notFoundEntity(String msg) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound(msg));
    }

    public static <T> ResponseEntity<ApiResponse<T>> badRequestEntity(String msg) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(badRequest(msg));
    }

    public static <T> ResponseEntity<ApiResponse<T>> unauthorizedEntity(String msg) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(unauthorized(msg));
    }
}
