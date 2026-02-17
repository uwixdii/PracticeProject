package org.example;

import java.awt.Color;

public class Status {

    public static String trRequestStatus(String englishStatus) {
        if (englishStatus == null) return "Неизвестно";

        return switch (englishStatus.toLowerCase()) {
            case "pending"           -> "Ожидает";
            case "partial_processed" -> "Частично";
            case "processed"         -> "Обработана";
            default                  -> englishStatus;
        };
    }

    public static Color getRequestStatusColor(String englishStatus) {
        if (englishStatus == null) return Color.WHITE;

        return switch (englishStatus.toLowerCase()) {
            case "pending"           -> Color.WHITE;
            case "partial_processed" -> new Color(255, 250, 205); // лимонный
            case "processed"         -> new Color(224, 255, 224); // светло-зелёный
            default                  -> Color.WHITE;
        };
    }

    public static String trItemStatus(String englishStatus) {
        if (englishStatus == null) return "Неизвестно";

        return switch (englishStatus.toLowerCase()) {
            case "pending"   -> "Ожидает рассмотрения";
            case "approved"  -> "Одобрено";
            case "rejected"  -> "Отклонено";
            default          -> englishStatus;
        };
    }

    public static Color getItemStatusColor(String englishStatus) {
        if (englishStatus == null) return Color.WHITE;

        return switch (englishStatus.toLowerCase()) {
            case "approved"  -> new Color(144, 238, 144); // зелёный
            case "rejected"  -> new Color(255, 99, 71);   // красный
            case "pending"   -> Color.WHITE;
            default          -> Color.WHITE;
        };
    }
}