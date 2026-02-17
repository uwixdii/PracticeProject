package org.example.Panels;

import org.example.*;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class RequestDetailDialog extends JDialog {

    private final JFrame parentFrame;
    private final User currentUser;
    private final int requestId;
    private final String requestNumber;
    private final Runnable onSaveCallback; // ← callback для обновления родительской таблицы

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID записи", "Товар", "Количество", "Статус"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false; // редактирование только через кнопки
        }
    };

    public RequestDetailDialog(JFrame parent, User user,
                               int reqId, String reqNumber,
                               Runnable onSaveCallback) {

        super(parent, "Заявка " + reqNumber, true);

        this.parentFrame = parent;
        this.currentUser = user;
        this.requestId = reqId;
        this.requestNumber = reqNumber;
        this.onSaveCallback = onSaveCallback;

        setSize(900, 600);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        JTable table = new JTable(model);
        table.setRowHeight(25);

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                                                           boolean sel, boolean focus,
                                                           int r, int c) {

                Component comp = super.getTableCellRendererComponent(t, val, sel, focus, r, c);

                String englishStatus = (String) t.getValueAt(r, 3);

                if (c == 3) {
                    setText(Status.trItemStatus(englishStatus));
                }

                comp.setBackground(Status.getItemStatusColor(englishStatus));

                if (sel) comp.setForeground(Color.BLACK);

                return comp;
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));

        boolean canManage = "Центр снабжения".equals(user.getRole())
                || "Админ".equals(user.getRole());

        boolean isArchived = isRequestArchived();

        if (canManage && !isArchived) {

            JButton approve = new JButton("Одобрить");
            JButton reject = new JButton("Отклонить");
            JButton changeQty = new JButton("Изменить кол-во");

            approve.addActionListener(e -> changeStatus(table, "approved"));
            reject.addActionListener(e -> changeStatus(table, "rejected"));
            changeQty.addActionListener(e -> changeQuantity(table));

            btnPanel.add(approve);
            btnPanel.add(reject);
            btnPanel.add(changeQty);
        }

        JButton historyBtn = new JButton("История изменений");
        historyBtn.addActionListener(e -> showHistory());
        btnPanel.add(historyBtn);

        // Кнопка удаления — всегда для админа
        if ("Админ".equals(user.getRole())) {
            JButton deleteBtn = new JButton("Удалить заявку");
            deleteBtn.setForeground(Color.RED);
            deleteBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
            deleteBtn.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Удалить заявку полностью? Это действие нельзя отменить!",
                        "Удаление заявки", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm == JOptionPane.YES_OPTION) {
                    try (Connection conn = DbManager.connect(Main.DB_NAME)) {
                        DbManager.deleteRequest(conn, requestId);
                        JOptionPane.showMessageDialog(this, "Заявка удалена");
                        if (onSaveCallback != null) {
                            onSaveCallback.run(); // обновляем таблицу
                        }
                        dispose();
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(this, "Ошибка удаления:\n" + ex.getMessage());
                    }
                }
            });
            btnPanel.add(deleteBtn);
        }

        if ("Админ".equals(user.getRole()) && isArchived) {
            JButton unarchiveBtn = new JButton("Вернуть из архива");
            unarchiveBtn.addActionListener(e -> unarchiveRequest());
            btnPanel.add(unarchiveBtn);
        }

        JButton closeBtn = new JButton("Закрыть");
        closeBtn.addActionListener(e -> dispose());
        btnPanel.add(closeBtn);

        add(btnPanel, BorderLayout.SOUTH);

        loadItems();
        setVisible(true);
    }

    private void loadItems() {
        model.setRowCount(0);
        try (Connection conn = DbManager.connect(Main.DB_NAME)) {
            List<RequestItemDetail> items = DbManager.getRequestItems(conn, requestId);
            for (RequestItemDetail ri : items) {
                model.addRow(new Object[]{
                        ri.getId(),
                        ri.getItemName(),
                        ri.getQuantity(),
                        ri.getStatus()
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Ошибка загрузки товаров заявки:\n" + ex.getMessage());
        }
    }

    private void changeStatus(JTable table, String newStatus) {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Выберите товар");
            return;
        }

        int recordId = (Integer) model.getValueAt(row, 0);

        if (JOptionPane.showConfirmDialog(this,
                "Изменить статус товара?",
                "Подтверждение",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }

        try (Connection conn = DbManager.connect(Main.DB_NAME)) {
            DbManager.updateRequestItemStatus(
                    conn,
                    recordId,
                    newStatus,
                    currentUser.getUsername()
            );

            model.setValueAt(newStatus, row, 3);

            JOptionPane.showMessageDialog(this, "Статус обновлён");

            if (onSaveCallback != null) {
                onSaveCallback.run(); // обновляем родительскую таблицу
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Ошибка: " + ex.getMessage());
        }
    }

    private void changeQuantity(JTable table) {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Выберите товар");
            return;
        }

        int current = (Integer) model.getValueAt(row, 2);
        String input = JOptionPane.showInputDialog(
                this,
                "Новое количество:",
                current
        );

        if (input == null) return;

        try {
            int newQty = Integer.parseInt(input.trim());

            if (newQty <= 0) {
                JOptionPane.showMessageDialog(this,
                        "Количество должно быть больше 0");
                return;
            }

            int recordId = (Integer) model.getValueAt(row, 0);

            try (Connection conn = DbManager.connect(Main.DB_NAME)) {
                DbManager.updateRequestItemQuantity(
                        conn,
                        recordId,
                        newQty,
                        currentUser.getUsername()
                );

                model.setValueAt(newQty, row, 2);

                JOptionPane.showMessageDialog(this, "Количество изменено");
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Введите корректное число");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Ошибка: " + ex.getMessage());
        }
        if (onSaveCallback != null) {
            onSaveCallback.run();
        }
    }

    private void showHistory() {
        try (Connection conn = DbManager.connect(Main.DB_NAME)) {
            List<HistoryEntry> history = DbManager.getRequestHistory(conn, requestId);

            if (history.isEmpty()) {
                JOptionPane.showMessageDialog(this, "История изменений пуста");
                return;
            }

            StringBuilder sb = new StringBuilder("История заявки " + requestNumber + "\n\n");

            for (HistoryEntry h : history) {
                sb.append(h.getTimestamp())
                        .append(" | ")
                        .append(h.getByUser())
                        .append(" | ")
                        .append(h.getAction());

                if (h.getDetails() != null && !h.getDetails().isEmpty()) {
                    sb.append(" → ")
                            .append(h.getDetails());
                }

                sb.append("\n\n");
            }

            JTextArea textArea = new JTextArea(sb.toString());
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);

            JScrollPane scroll = new JScrollPane(textArea);
            scroll.setPreferredSize(new Dimension(600, 400));

            JOptionPane.showMessageDialog(
                    this,
                    scroll,
                    "История изменений",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Ошибка загрузки истории:\n" + ex.getMessage());
        }
    }

    private void unarchiveRequest() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Вернуть заявку в работу? Все статусы товаров сбросятся на 'pending'.",
                "Подтверждение", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try (Connection conn = DbManager.connect(Main.DB_NAME)) {
                DbManager.unarchiveRequest(conn, requestId);
                JOptionPane.showMessageDialog(this, "Заявка возвращена в работу");

                if (onSaveCallback != null) {
                    onSaveCallback.run(); // ← обновляем родительскую таблицу
                }

                dispose();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Ошибка возврата:\n" + ex.getMessage());
            }
        }
    }

    private boolean isRequestArchived() {
        try (Connection conn = DbManager.connect(Main.DB_NAME)) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT archived FROM requests WHERE id = ?")) {
                ps.setInt(1, requestId);
                ResultSet rs = ps.executeQuery();
                return rs.next() && rs.getInt("archived") == 1;
            }
        } catch (SQLException ex) {
            return false;
        }
    }
}