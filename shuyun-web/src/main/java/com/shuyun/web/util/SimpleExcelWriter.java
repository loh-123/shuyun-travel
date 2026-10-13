package com.shuyun.web.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 极简 XLSX 写入器
 *
 * <p>不依赖 Apache POI，直接按 OOXML 规范拼装一个单工作表的 .xlsx（本质为 zip + XML）。
 * 仅支持「表头 + 文本行」的简单导出场景，单元格统一使用内联字符串（inlineStr），
 * 无需共享字符串表，足以满足后台列表导出需求。</p>
 */
public final class SimpleExcelWriter {

    private SimpleExcelWriter() {
    }

    /**
     * 写出 XLSX
     *
     * @param out       输出流（写出完成后会被关闭）
     * @param sheetName 工作表名
     * @param headers   表头
     * @param rows      数据行（每行元素与表头一一对应，null 视为空字符串）
     * @throws IOException 写出异常
     */
    public static void write(OutputStream out, String sheetName, String[] headers, List<String[]> rows)
            throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            putEntry(zip, "[Content_Types].xml", contentTypes());
            putEntry(zip, "_rels/.rels", rootRels());
            putEntry(zip, "xl/workbook.xml", workbook(sheetName));
            putEntry(zip, "xl/_rels/workbook.xml.rels", workbookRels());
            putEntry(zip, "xl/worksheets/sheet1.xml", sheet(headers, rows));
        }
    }

    /** 写入单个 zip 条目 */
    private static void putEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "</Types>";
    }

    private static String rootRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private static String workbook(String sheetName) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"" + escape(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                + "</workbook>";
    }

    private static String workbookRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "</Relationships>";
    }

    /** 拼装工作表 XML */
    private static String sheet(String[] headers, List<String[]> rows) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<sheetData>");

        int rowIndex = 1;
        sb.append(row(rowIndex++, headers));
        if (rows != null) {
            for (String[] data : rows) {
                sb.append(row(rowIndex++, data));
            }
        }

        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    /** 拼装单行 XML */
    private static String row(int rowIndex, String[] cells) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("<row r=\"").append(rowIndex).append("\">");
        for (int col = 0; col < cells.length; col++) {
            String ref = columnName(col) + rowIndex;
            String value = cells[col] == null ? "" : cells[col];
            sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(escape(value))
                    .append("</t></is></c>");
        }
        sb.append("</row>");
        return sb.toString();
    }

    /** 列序号转列名：0 -> A，25 -> Z，26 -> AA */
    private static String columnName(int index) {
        StringBuilder sb = new StringBuilder();
        int i = index;
        do {
            sb.insert(0, (char) ('A' + i % 26));
            i = i / 26 - 1;
        } while (i >= 0);
        return sb.toString();
    }

    /** XML 转义（含非法控制字符剔除） */
    private static String escape(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> {
                    // 剔除 XML 1.0 不允许的控制字符，避免生成的文件损坏
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
