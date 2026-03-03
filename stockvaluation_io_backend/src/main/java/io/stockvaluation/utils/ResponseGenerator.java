package io.stockvaluation.utils;


import io.stockvaluation.dto.FieldErrorDTO;
import io.stockvaluation.dto.ResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is response class used to generate api responses.
 *
 * @author Pushkar Bisht
 * @date 22-Oct-2024
 */
@Slf4j
public class ResponseGenerator {
    /**
     * This method will return the controller response.
     *
     * @return {@link ResponseEntity} of {@link ResponseDTO}
     */
    public static <T> ResponseEntity<ResponseDTO<T>> generateSuccessResponse(T data) {
        ResponseDTO<T> responseDTO = new ResponseDTO(data);
        log.info("Sent response successfully.");
        return new ResponseEntity<ResponseDTO<T>>(responseDTO, HttpStatus.OK);
    }

    public static <T> ResponseEntity<ResponseDTO<T>> generateCreatedResponse(T data) {
        ResponseDTO<T> responseDTO = new ResponseDTO(data, HttpStatus.CREATED.value());
        return new ResponseEntity<ResponseDTO<T>>(responseDTO, HttpStatus.CREATED);
    }

    public static <T> ResponseEntity<ResponseDTO<T>> generateConflictResponse(String message) {
        ResponseDTO<T> responseDTO = new ResponseDTO(null, message, false, HttpStatus.CONFLICT.value());
        return new ResponseEntity<ResponseDTO<T>>(responseDTO, HttpStatus.CONFLICT);
    }

    public static <T> ResponseEntity<ResponseDTO<T>> generateBadRequestResponse(String message) {
        ResponseDTO<T> responseDTO = new ResponseDTO(null, message, false, HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<ResponseDTO<T>>(responseDTO, HttpStatus.BAD_REQUEST);
    }

    public static <T> ResponseEntity<Object> generateBadRequestResponse(List<FieldErrorDTO> fieldErrors) {
        ResponseDTO<T> responseDTO = new ResponseDTO<>(fieldErrors);
        return new ResponseEntity<>(responseDTO, HttpStatus.BAD_REQUEST);
    }

    public static <T> ResponseEntity<ResponseDTO<T>> generateInternalServerErrorResponse(String message) {
        ResponseDTO<T> responseDTO = new ResponseDTO<T>(null, message, false, HttpStatus.INTERNAL_SERVER_ERROR.value());
        return new ResponseEntity<>(responseDTO, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public static <T> ResponseEntity<ResponseDTO<T>> generateUnprocessableEntityResponse(String message) {
        ResponseDTO<T> responseDTO = new ResponseDTO<T>(null, message, false, HttpStatus.UNPROCESSABLE_ENTITY.value());
        return new ResponseEntity<>(responseDTO, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static <T> ResponseEntity<ResponseDTO<T>> generateAccessDeniedResponse(String message) {
        ResponseDTO<T> responseDTO = new ResponseDTO<T>(null, message, false, HttpStatus.FORBIDDEN.value());
        return new ResponseEntity<>(responseDTO, HttpStatus.FORBIDDEN);
    }

    public static <T> ResponseEntity<ResponseDTO<T>> generateAuthenticationExceptionResponse(String message) {
        ResponseDTO<T> responseDTO = new ResponseDTO<T>(null, message, false, HttpStatus.UNAUTHORIZED.value());
        return new ResponseEntity<>(responseDTO, HttpStatus.UNAUTHORIZED);
    }

    public static <T> ResponseEntity<ResponseDTO<T>> generateExceptionResponseDTO(Exception exception) {
        ResponseDTO<T> responseDTO = new ResponseDTO<T>(null, exception.getMessage(), false, HttpStatus.INTERNAL_SERVER_ERROR.value());
        return new ResponseEntity<>(responseDTO, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public static <T> ResponseEntity<ResponseDTO<T>> generateNotFoundResponse(String message) {
        ResponseDTO<T> responseDTO = new ResponseDTO<>(null, message, false, HttpStatus.NOT_FOUND.value());
        return new ResponseEntity<>(responseDTO, HttpStatus.NOT_FOUND);
    }

    public static <T> ResponseEntity<ResponseDTO<T>> generateAlreadyExistResponse(T data) {
        ResponseDTO<T> responseDTO = new ResponseDTO(data, null, false, HttpStatus.CONFLICT.value());
        return new ResponseEntity<ResponseDTO<T>>(responseDTO, HttpStatus.CONFLICT);
    }

    public static ResponseEntity<Object> generateCustomResponse(String exception, HttpStatus status, boolean success) {
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("success", success);
        responseMap.put("message", exception);
        responseMap.put("timestamp", System.currentTimeMillis());
        return new ResponseEntity<>(responseMap, status);
    }
}
