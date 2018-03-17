import ballerina/auth.jwtAuth;
import ballerina/net.http;
import ballerina/mime;

function testCanHandleHttpJwtAuthWithoutHeader () (boolean) {
    jwtAuth:HttpJwtAuthnHandler handler = {};
    http:InRequest inRequest = {rawPath:"/helloWorld/sayHello", method:"GET", httpVersion:"1.1",
                                   userAgent:"curl/7.35.0", extraPathInfo:"null"};
    string[] authHeaderValue = ["Basic xxxxxx"];
    mime:Entity requestEntity = {headers:{"Authorization":authHeaderValue}};
    inRequest.setEntity(requestEntity);
    return handler.canHandle(inRequest);
}

function testCanHandleHttpJwtAuth () (boolean) {
    jwtAuth:HttpJwtAuthnHandler handler = {};
    http:InRequest inRequest = {rawPath:"/helloWorld/sayHello", method:"GET", httpVersion:"1.1",
                                   userAgent:"curl/7.35.0", extraPathInfo:"null"};
    string[] authHeaderValue = ["Bearer xxx.yyy.zzz"];
    mime:Entity requestEntity = {headers:{"Authorization":authHeaderValue}};
    inRequest.setEntity(requestEntity);
    return handler.canHandle(inRequest);
}

function testHandleHttpJwtAuth (string token) (boolean) {
    jwtAuth:HttpJwtAuthnHandler handler = {};
    http:InRequest inRequest = {rawPath:"/helloWorld/sayHello", method:"GET", httpVersion:"1.1",
                                   userAgent:"curl/7.35.0", extraPathInfo:"null"};
    string[] authHeaderValue = ["Bearer " + token];
    mime:Entity requestEntity = {headers:{"Authorization":authHeaderValue}};
    inRequest.setEntity(requestEntity);
    return handler.handle(inRequest);
}
