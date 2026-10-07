package com.horsetrack.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> { // Phải có <T> ở đây
    private int status;
    private String message;
    private T data; // Kiểu dữ liệu chỗ này là T thay vì Object hay String
}