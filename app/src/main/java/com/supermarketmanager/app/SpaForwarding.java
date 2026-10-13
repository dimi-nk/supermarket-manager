package com.supermarketmanager.app;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Client-side routes of the React app resolve to index.html. */
@Controller
public class SpaForwarding {

    @GetMapping({"/pantry", "/pantry/**", "/rules", "/rules/**", "/prices", "/prices/**", "/supermarkets", "/basket", "/basket/**",
            "/runs", "/runs/**", "/agents"})
    public String forward() {
        return "forward:/index.html";
    }
}
