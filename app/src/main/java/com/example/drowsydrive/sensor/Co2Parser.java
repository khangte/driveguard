package com.example.drowsydrive.sensor;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 아두이노 펌웨어(driveguard_co2_bluetooth.ino)의 줄 단위 전송을 파싱한다.
 * 전송 포맷: "CO2: <ppm>\r\n", "Status: ...\r\n", "Bluetooth Ready\r\n", "Error reading CO2 value\r\n".
 * 블루투스 수신은 한 줄이 여러 번에 나뉘거나 여러 줄이 한 번에 올 수 있으므로 '\n'까지 바이트를 모은다.
 */
public final class Co2Parser {
    private static final String CO2_PREFIX = "CO2:";
    private static final int MAX_LINE_BYTES = 256; // 줄바꿈 없는 비정상 데이터로 버퍼가 커지지 않도록 제한

    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();

    /** 수신한 바이트를 누적하고, 완성된 줄 중 CO2 값만 순서대로 반환한다. */
    public List<Integer> feed(byte[] data, int length) {
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            byte b = data[i];
            if (b == '\n') {
                Integer value = parseLine(new String(lineBuffer.toByteArray(), StandardCharsets.UTF_8));
                if (value != null) {
                    values.add(value);
                }
                lineBuffer.reset();
            } else if (lineBuffer.size() < MAX_LINE_BYTES) {
                lineBuffer.write(b);
            }
        }
        return values;
    }

    /** "CO2: 1234" 형식의 줄이면 ppm 값을, 아니면 null을 반환한다. */
    static Integer parseLine(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith(CO2_PREFIX)) {
            return null;
        }
        try {
            return Integer.parseInt(trimmed.substring(CO2_PREFIX.length()).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
