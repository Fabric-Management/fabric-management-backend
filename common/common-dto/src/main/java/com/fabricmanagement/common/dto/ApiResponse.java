package com.fabricmanagement.common.dto;

public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private int statusCode;

    public ApiResponse(){}

    private ApiResponse(boolean success, String message, T data, int statusCode){
        this.success=success; this.message=message; this.data=data; this.statusCode=statusCode;
    }

    public static <T> ApiResponse<T> ok(T data){ return new ApiResponse<>(true,"OK",data,200); }
    public static <T> ApiResponse<T> ok(String msg, T data){ return new ApiResponse<>(true,msg,data,200); }
    public static <T> ApiResponse<T> fail(String msg, int code){ return new ApiResponse<>(false,msg,null,code); }

    public boolean isSuccess(){ return success; }
    public void setSuccess(boolean success){ this.success = success; }
    public String getMessage(){ return message; }
    public void setMessage(String message){ this.message = message; }
    public T getData(){ return data; }
    public void setData(T data){ this.data = data; }
    public int getStatusCode(){ return statusCode; }
    public void setStatusCode(int statusCode){ this.statusCode = statusCode; }
}