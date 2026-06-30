package com.sm2sdk.demo.boot3;

import com.sm2sdk.client.Sm2HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DemoController {

    @Autowired(required = false)
    private Sm2HttpClient sm2Client;

    @PostMapping("/api/echo")
    public Map<String, Object> echo(@RequestBody Map<String, Object> body) {
        body.put("serverTime", System.currentTimeMillis());
        return body;
    }

    @GetMapping("/api/call-peer")
    public Object callPeer() {
        if (sm2Client == null) {
            return Map.of("error", "配置 sm2.sdk.server-url 以启用主动调用");
        }
        return sm2Client.get("/api/peer/info").execute(Object.class);
    }
}
