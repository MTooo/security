package com.sm2sdk.demo.boot3;


import com.sm2sdk.client.Sm2HttpClient;
import com.sm2sdk.core.model.Sm2SdkConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class DemoControllerTest {

    @Autowired
    private Sm2HttpClient sm2HttpClient;

    @Test
    void testEcho() {
        Map<String,String> body = new HashMap<>();
        body.put("name","张三");
        Object execute = sm2HttpClient.post("/api/echo")
                .body(body)
                .execute(Object.class);
        System.out.println(execute);
    }

}
