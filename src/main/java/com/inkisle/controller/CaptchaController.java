package com.inkisle.controller;

import com.inkisle.util.CaptchaUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    /** 验证码在 Session 里的 key */
    private static final String CAPTCHA_KEY = "captcha";

    /**
     * 生成验证码图片，答案先存到 Session，登录时再取出来比对
     */
    @GetMapping("/image")
    public void image(HttpSession session, HttpServletResponse response) throws IOException {
        String code = CaptchaUtil.randomCode();
        session.setAttribute(CAPTCHA_KEY, code);

        BufferedImage image = CaptchaUtil.createImage(code);
        response.setContentType("image/png");
        // 不缓存，否则浏览器会一直显示同一张
        response.setHeader("Cache-Control", "no-store");
        ImageIO.write(image, "png", response.getOutputStream());
    }

}
