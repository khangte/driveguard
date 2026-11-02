package com.example.drowsydrive.sensor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class Co2ParserTest {

    private static List<Integer> feed(Co2Parser parser, String chunk) {
        byte[] bytes = chunk.getBytes(StandardCharsets.UTF_8);
        return parser.feed(bytes, bytes.length);
    }

    @Test
    public void parsesFirmwareOutputAndIgnoresOtherLines() {
        Co2Parser parser = new Co2Parser();
        List<Integer> values = feed(parser,
                "Bluetooth Ready\r\nCO2: 650\r\nStatus: 정상 (1)\r\nError reading CO2 value\r\nCO2: 2100\r\nStatus: 비상 (3)\r\n");
        assertEquals(Arrays.asList(650, 2100), values);
    }

    @Test
    public void joinsLineSplitAcrossChunks() {
        Co2Parser parser = new Co2Parser();
        assertTrue(feed(parser, "CO").isEmpty());
        assertTrue(feed(parser, "2: 12").isEmpty());
        assertTrue(feed(parser, "34\r").isEmpty()); // 줄바꿈 전까지는 값 없음
        assertEquals(Arrays.asList(1234), feed(parser, "\nCO2: 9"));
        assertEquals(Arrays.asList(9), feed(parser, "\r\n"));
    }

    @Test
    public void handlesMultibyteCharacterSplitAcrossChunks() {
        Co2Parser parser = new Co2Parser();
        byte[] bytes = "Status: 주의 (2)\r\nCO2: 900\r\n".getBytes(StandardCharsets.UTF_8);
        int split = "Status: ".length() + 1; // '주'(3바이트) 중간에서 분할
        assertTrue(parser.feed(Arrays.copyOfRange(bytes, 0, split), split).isEmpty());
        byte[] rest = Arrays.copyOfRange(bytes, split, bytes.length);
        assertEquals(Arrays.asList(900), parser.feed(rest, rest.length));
    }

    @Test
    public void respectsLengthArgument() {
        Co2Parser parser = new Co2Parser();
        byte[] buffer = "CO2: 700\nCO2: 800\n".getBytes(StandardCharsets.UTF_8);
        assertEquals(Arrays.asList(700), parser.feed(buffer, 9)); // 버퍼 뒷부분은 이전 데이터
    }

    @Test
    public void parseLine_rejectsMalformedValues() {
        assertNull(Co2Parser.parseLine("CO2: abc"));
        assertNull(Co2Parser.parseLine("CO2:"));
        assertNull(Co2Parser.parseLine("Status: 정상 (1)"));
        assertEquals(Integer.valueOf(400), Co2Parser.parseLine("  CO2:400 \r"));
    }
}
