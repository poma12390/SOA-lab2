package se.ifmo.model.exception;

public class RouteNotFoundException extends RuntimeException{
    private String message;
    public RouteNotFoundException(String s){
        message = s;
    }
}
