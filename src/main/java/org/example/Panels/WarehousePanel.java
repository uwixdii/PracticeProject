package org.example.Panels;

import org.example.*;
import org.example.Panels.admin.AdminPanel;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WarehousePanel extends JPanel {

    private final JFrame mainFrame;
    private final boolean isAdminMode;
    private final User currentUser;

    private final DefaultTableModel approvedModel = new DefaultTableModel(
            new String[]{"№ заявки", "Товар", "Одобрено шт.", "Дата одобрения"}, 0);

    private final DefaultTableModel stockModel = new DefaultTableModel(
            new String[]{"ID", "Наименование", "Остаток на складе"}, 0);

    public WarehousePanel(JFrame frame, boolean adminMode, User user) {
        this.mainFrame = frame;
        this.isAdminMode = adminMode;
        this.currentUser = user;

        setLayout(new BorderLayout(10, 10));

        JLabel title = new JLabel("Склад — " + user.getUsername(), SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        add(title, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setResizeWeight(0.5);

        // Верх — одобренные позиции
        JPanel top = new JPanel(new BorderLayout());
        top.add(new JLabel("Одобренные поставки"), BorderLayout.NORTH);
        JTable approvedTable = new JTable(approvedModel);
        top.add(new JScrollPane(approvedTable), BorderLayout.CENTER);
        split.setTopComponent(top);

        // Низ — текущие остатки на складе
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(new JLabel("Текущие остатки на складе"), BorderLayout.NORTH);
        JTable stockTable = new JTable(stockModel);
        bottom.add(new JScrollPane(stockTable), BorderLayout.CENTER);
        split.setBottomComponent(bottom);

        add(split, BorderLayout.CENTER);

        // Навигация
        if (isAdminMode) {
            JButton back = new JButton("← Назад в админ-панель");
            back.addActionListener(e -> {
                mainFrame.getContentPane().removeAll();
                mainFrame.add(new AdminPanel(mainFrame, currentUser));
                mainFrame.revalidate();
                mainFrame.repaint();
            });
            add(back, BorderLayout.SOUTH);
        } else {
            JButton exit = new JButton("Выйти");
            exit.addActionListener(e -> {
                mainFrame.dispose();
                Main.createLoginWindow();
            });
            add(exit, BorderLayout.SOUTH);
        }

        loadData();
    }

    public void loadData() {
        approvedModel.setRowCount(0);
        stockModel.setRowCount(0);

        try (Connection conn = DbManager.connect(Main.DB_NAME)) {
            // Одобренные позиции
            String sql = """
                SELECT r.number, i.name, ri.quantity, ri.status, ri.created_at
                FROM request_items ri
                JOIN items i ON i.id = ri.item_id
                JOIN requests r ON r.id = ri.request_id
                WHERE ri.status = 'approved'
                ORDER BY ri.created_at DESC
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    approvedModel.addRow(new Object[]{
                            rs.getString("number"),
                            rs.getString("name"),
                            rs.getInt("quantity"),
                            rs.getString("created_at")
                    });
                }
            }

            List<Item> items = DbManager.getAllItems(conn);
            for (Item i : items) {
                stockModel.addRow(new Object[]{i.getId(), i.getName(), i.getQuantity()});
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Ошибка загрузки данных склада");
        }
    }
}