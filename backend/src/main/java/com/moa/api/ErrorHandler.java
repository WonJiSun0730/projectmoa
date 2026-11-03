package com.moa.api;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
@RestControllerAdvice
public class ErrorHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> domain(ApiException e) { return ResponseEntity.status(e.status).body(Map.of("message",e.getMessage())); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException e) {
        var error=e.getBindingResult().getFieldErrors().getFirst();
        return ResponseEntity.badRequest().body(Map.of("message",error.getDefaultMessage(),"field",error.getField()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict() { return ResponseEntity.status(409).body(Map.of("message","이미 사용 중인 정보이거나 중복 요청입니다.")); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> size() { return ResponseEntity.status(413).body(Map.of("message","이미지는 각각 5MB 이하로 업로드해 주세요.")); }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> malformed() { return ResponseEntity.badRequest().body(Map.of("message","요청 형식을 확인해 주세요.")); }
}
