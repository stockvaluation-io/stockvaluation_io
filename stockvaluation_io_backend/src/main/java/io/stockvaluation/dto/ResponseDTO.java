package io.stockvaluation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

/**
 * Data Transfer Object for API responses.
 *
 * @author Pushkar Bisht
 * @Date 21-Oct-2024
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(value = JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResponseDTO<T> implements Serializable {
    private static final long serialVersionUID = -6072518716971093796L;
    private transient T data;
    private String message;
    private boolean success;
    private int httpStatus;
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    private String errorCode;
    private List<FieldErrorDTO> errors;
    private long timestamp = System.currentTimeMillis();

    public ResponseDTO(T data, String message, boolean success, int httpStatus) {
        super();
        this.data = data;
        this.message = message;
        this.success = success;
        this.httpStatus = httpStatus;
    }

    public ResponseDTO(T data) {
        super();
        this.data = data;
        this.success = true;
        this.httpStatus = HttpStatus.OK.value();
    }

    public ResponseDTO(T data, int httpStatus) {
        super();
        this.data = data;
        this.success = true;
        this.httpStatus = httpStatus;
    }

    public ResponseDTO(List<FieldErrorDTO> errors) {
        this.errors = errors;
        success = false;
        httpStatus = HttpStatus.BAD_REQUEST.value();
    }

    public ResponseDTO(T data, String message, boolean isSuccess, int httpStatus, String errorCode) {
        super();
        this.data = data;
        this.message = message;
        this.success = isSuccess;
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.timestamp = new Timestamp(System.currentTimeMillis()).getTime();
    }
}