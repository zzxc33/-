package com.music.recommendation.config;

import com.music.recommendation.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    /** URL 參數類型錯誤：例如 /playlists/mine 匹配到 /playlists/{id} 時 "mine" 無法轉 Long */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseBody
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String msg = "參數 '" + e.getName() + "' 值 '" + e.getValue() + "' 類型錯誤";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.badRequest(msg));
    }

    /** 通用兜底 */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e) {
        String msg = "服務器內部錯誤: " + e.getMessage();
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(500, msg));
    }
}