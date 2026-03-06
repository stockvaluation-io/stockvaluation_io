package io.stockvaluation.utils;

import io.stockvaluation.dto.FieldErrorDTO;
import io.stockvaluation.dto.ResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResponseGeneratorTest {

    @Test
    void generateSuccessResponse_returnsOk() {
        ResponseEntity<ResponseDTO<String>> response = ResponseGenerator.generateSuccessResponse("ok");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("ok", response.getBody().getData());
    }

    @Test
    void generateCreatedResponse_returnsCreated() {
        ResponseEntity<ResponseDTO<String>> response = ResponseGenerator.generateCreatedResponse("created");
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(HttpStatus.CREATED.value(), response.getBody().getHttpStatus());
    }

    @Test
    void generateConflictResponse_returnsConflict() {
        ResponseEntity<ResponseDTO<String>> response = ResponseGenerator.generateConflictResponse("conflict");
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("conflict", response.getBody().getMessage());
    }

    @Test
    void generateBadRequestResponse_withMessage_returnsBadRequest() {
        ResponseEntity<ResponseDTO<String>> response = ResponseGenerator.generateBadRequestResponse("bad");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("bad", response.getBody().getMessage());
    }

    @Test
    void generateBadRequestResponse_withFieldErrors_returnsBadRequest() {
        List<FieldErrorDTO> errors = List.of(new FieldErrorDTO("ticker", "required", "MISSING"));

        ResponseEntity<Object> response = ResponseGenerator.generateBadRequestResponse(errors);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ResponseDTO<?> body = (ResponseDTO<?>) response.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());
        assertNotNull(body.getErrors());
        assertEquals(1, body.getErrors().size());
    }

    @Test
    void generateInternalServerErrorResponse_returnsInternalServerError() {
        ResponseEntity<ResponseDTO<String>> response = ResponseGenerator.generateInternalServerErrorResponse("err");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void generateUnprocessableEntityResponse_returnsUnprocessableEntity() {
        ResponseEntity<ResponseDTO<String>> response = ResponseGenerator.generateUnprocessableEntityResponse("unprocessable");
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void generateAccessDeniedResponse_returnsForbidden() {
        ResponseEntity<ResponseDTO<String>> response = ResponseGenerator.generateAccessDeniedResponse("forbidden");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void generateAuthenticationExceptionResponse_returnsUnauthorized() {
        ResponseEntity<ResponseDTO<String>> response = ResponseGenerator.generateAuthenticationExceptionResponse("unauthorized");
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void generateExceptionResponseDTO_returnsInternalServerError() {
        ResponseEntity<ResponseDTO<String>> response = ResponseGenerator.generateExceptionResponseDTO(new RuntimeException("boom"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("boom", response.getBody().getMessage());
    }

    @Test
    void generateNotFoundResponse_returnsNotFound() {
        ResponseEntity<ResponseDTO<String>> response = ResponseGenerator.generateNotFoundResponse("missing");
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void generateAlreadyExistResponse_returnsConflict() {
        ResponseEntity<ResponseDTO<String>> response = ResponseGenerator.generateAlreadyExistResponse("exists");
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("exists", response.getBody().getData());
    }

    @Test
    void generateCustomResponse_returnsMap() {
        ResponseEntity<Object> response = ResponseGenerator.generateCustomResponse("x", HttpStatus.ACCEPTED, true);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(true, body.get("success"));
        assertEquals("x", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }
}
