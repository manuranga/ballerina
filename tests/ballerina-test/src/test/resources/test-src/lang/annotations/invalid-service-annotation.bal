import lang.annotations.doc1 as doc;
import ballerina/net.http;

@doc:Description{value:1234}
service<http> PizzaService {
    
    @doc:Description{value:"Order pizza"}
    resource orderPizza(http:Connection conn, http:InRequest req) {
        http:OutResponse res = {};
        _ = conn.respond(res);
    }
    
    @doc:Description{value:"Check order status"}
    resource checkStatus(http:Connection conn, http:InRequest req) {
        http:OutResponse res = {};
        _ = conn.respond(res);
    }
}
