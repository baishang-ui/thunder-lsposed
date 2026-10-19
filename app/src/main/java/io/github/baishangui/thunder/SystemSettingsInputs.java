package io.github.baishangui.thunder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Data conversion for the native color editor and document importer. */
final class SystemSettingsInputs {
    private static final int MAX_JSON_BYTES = 1024 * 1024;
    private SystemSettingsInputs() { }

    static int parseNumber(String text, int min, int max) {
        try {
            int number = Integer.parseInt(text.trim());
            if (number >= min && number <= max) return number;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("请输入 " + min + "–" + max + " 范围内的整数");
    }
    static int channel(int color, int shift) {
        checkShift(shift);
        return (color >>> shift) & 255;
    }
    static int withChannel(int color, int shift, int value) {
        checkShift(shift);
        if (value < 0 || value > 255) throw new IllegalArgumentException("颜色通道范围为 0–255");
        return (color & ~(255 << shift)) | (value << shift);
    }
    private static void checkShift(int shift) {
        if (shift != 0 && shift != 8 && shift != 16 && shift != 24)
            throw new IllegalArgumentException("未知颜色通道");
    }
    static String readJson(InputStream stream) throws IOException {
        if (stream == null) throw new IOException("无法打开 JSON 文件");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (output.size() + count > MAX_JSON_BYTES) throw new IOException("配置文件不能超过 1 MB");
            output.write(buffer, 0, count);
        }
        String text = output.toString(StandardCharsets.UTF_8.name());
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        if (text.trim().isEmpty()) throw new IOException("JSON 文件为空");
        return text;
    }
}
