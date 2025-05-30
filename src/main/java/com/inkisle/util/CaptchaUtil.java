package com.inkisle.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;

/**
 * 验证码图片生成工具
 */
public class CaptchaUtil {

    /** 排除易混淆的字符 0/O、1/I */
    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private static final int CODE_LENGTH = 4;

    private static final int WIDTH = 120;

    private static final int HEIGHT = 40;

    private static final SecureRandom RANDOM = new SecureRandom();

    private CaptchaUtil() {
    }

    /**
     * 随机生成 4 位验证码
     */
    public static String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 把验证码画成图片
     */
    public static BufferedImage createImage(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 26));
        for (int i = 0; i < code.length(); i++) {
            g.drawString(String.valueOf(code.charAt(i)), 18 + i * 24, 30);
        }

        // 加几条干扰线，降低被机器识别的概率
        g.setColor(Color.GRAY);
        for (int i = 0; i < 5; i++) {
            g.drawLine(RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT),
                    RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT));
        }

        g.dispose();
        return image;
    }

}
