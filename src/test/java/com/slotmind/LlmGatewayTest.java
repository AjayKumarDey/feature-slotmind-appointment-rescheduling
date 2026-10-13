package com.slotmind;
import com.slotmind.ai.HttpLlmGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class LlmGatewayTest {
    @Test void ollamaHttpContract() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/chat",e->{
            var request=new ObjectMapper().readTree(e.getRequestBody());
            assertEquals("json",request.path("format").asText());assertFalse(request.path("stream").asBoolean());
            byte[] body="{\"message\":{\"content\":\"{\\\"slotId\\\":\\\"S2\\\"}\"}}".getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);e.close();
        });server.start();
        try{
            var g=new HttpLlmGateway(new ObjectMapper(),"ollama","http://127.0.0.1:"+server.getAddress().getPort()+"/api/chat","test-model","",2);
            assertEquals("{\"slotId\":\"S2\"}",g.callLLM("Return JSON"));
        }finally{server.stop(0);}
    }
    @Test void compatibleHttpContract() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/chat",e->{
            assertEquals("Bearer test-key",e.getRequestHeaders().getFirst("Authorization"));
            var request=new ObjectMapper().readTree(e.getRequestBody());assertEquals("json_object",request.path("response_format").path("type").asText());
            byte[] body="{\"choices\":[{\"message\":{\"content\":\"result\"}}]}".getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);e.close();
        });server.start();
        try{assertEquals("result",new HttpLlmGateway(new ObjectMapper(),"openai-compatible","http://127.0.0.1:"+server.getAddress().getPort()+"/chat","model","test-key",2).callLLM("JSON"));}
        finally{server.stop(0);}
    }
}
