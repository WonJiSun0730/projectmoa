package com.moa.api;
import org.springframework.http.HttpStatus;
public class ApiException extends RuntimeException {
    public final HttpStatus status;
    public ApiException(HttpStatus status, String message) { super(message); this.status=status; }
    public static ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST,message); }
    public static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND,"요청한 콘텐츠를 찾을 수 없습니다."); }
    public static ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN,"접근 권한이 없습니다."); }
}
