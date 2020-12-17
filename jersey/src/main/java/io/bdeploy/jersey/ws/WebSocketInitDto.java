package io.bdeploy.jersey.ws;

import java.util.ArrayList;
import java.util.List;

public class WebSocketInitDto {

    public String token;

    public List<String> scope = new ArrayList<>();

}
