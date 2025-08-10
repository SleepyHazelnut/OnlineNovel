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

    /**
     * 生成验证码图片，答案先存入 Session，登录时由 CaptchaUtil.verify 取出比对
     */
    @GetMapping("/image")
    public void image(HttpSession session, HttpServletResponse response) throws IOException {
        String code = CaptchaUtil.randomCode();
        session.setAttribute(CaptchaUtil.SESSION_KEY, code);

        BufferedImage image = CaptchaUtil.createImage(code);
        response.setContentType("image/png");
        // 必须禁用缓存，否则浏览器会复用旧图，导致用户误以为验证码未刷新
        response.setHeader("Cache-Control", "no-store");
        ImageIO.write(image, "png", response.getOutputStream());
    }

}
