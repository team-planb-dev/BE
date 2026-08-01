package com.planb.global.utils.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    public Cookie zeroCookie(HttpServletResponse response){

        Cookie cookie = new Cookie("refreshToken",null);
        cookie.setMaxAge(0);
        cookie.setPath("/");

        return cookie;
    }

    public Cookie createCookie(String key,String value){

        Cookie cookie = new Cookie(key,value);
        cookie.setMaxAge(24*60*60);
        cookie.setHttpOnly(true);
        return cookie;

    }

    public String findCookie(HttpServletRequest request){

        String refresh = null;
        Cookie[] cookies = request.getCookies();
        if(cookies != null){
            for(Cookie cookie : cookies){
                if("refreshToken".equals(cookie.getName())){
                    refresh = cookie.getValue();
                }
            }
        }

        return refresh;
    }
}
