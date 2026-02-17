package org.example.Panels;

import org.example.*;
import org.example.Panels.admin.AdminPanel;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SupplyCenterPanel extends JPanel {

    private final JFrame mainFrame;
    private final boolean isAdminMode;
    private final User currentUser;

    private DefaultTableModel currentModel;
    private DefaultTableModel archiveModel;

    public SupplyCenterPanel(JFrame frame, boolean adminMode, User user) {
        this.mainFrame = frame;
        this.isAdminMode = adminMode;
        this.currentUser = user;

        setLayout(new BorderLayout(10, 10));

        JLabel title = new JLabel("Центр снабжения — " + user.getUsername(), SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        add(title, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();

        tabs.addTab("Текущие", createRequestsPanel(false));
        tabs.addTab("Архив", createRequestsPanel(true));

        add(tabs, BorderLayout.CENTER);

        if (isAdminMode) {
            JButton back = new JButton("← Назад в админ-панель");
            back.addActionListener(e -> switchToAdminPanel());
            add(back, BorderLayout.SOUTH);
        } else {
            JButton exit = new JButton("Выйти");
            exit.addActionListener(e -> {
                mainFrame.dispose();
                Main.createLoginWindow();
            });
            add(exit, BorderLayout.SOUTH);
        }
    }

    private JPanel createRequestsPanel(boolean archived) {

        JPanel panel = new JPanel(new BorderLayout());

        DefaultTableModel model = new DefaultTableModel(
                new String[]{"№ заявки", "Цех", "Статус", "Дата", "Позиций", "Всего ед."}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        if (archived) {
            archiveModel = model;
        } else {
            currentModel = model;
        }

        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object val, boolean sel, boolean focus, int r, int c) {

                Component comp = super.getTableCellRendererComponent(t, val, sel, focus, r, c);

                String status = (String) t.getValueAt(r, 2);

                if (c == 2) {
                    setText(Status.trRequestStatus(status));
                }

                comp.setBackground(Status.getRequestStatusColor(status));

                return comp;
            }
        });

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {

                if (table.isEditing()) {
                    table.getCellEditor().stopCellEditing();
                }

                if (e.getClickCount() == 2) {

                    int row = table.rowAtPoint(e.getPoint());
                    if (row < 0) return;

                    table.setRowSelectionInterval(row, row);
                    String number = (String) model.getValueAt(row, 0);

                    try (Connection conn = DbManager.connect(Main.DB_NAME)) {

                        int requestId = getRequestIdByNumber(conn, number);

                        if (requestId >= 0) {
                            new RequestDetailDialog(
                                    mainFrame,
                                    currentUser,
                                    requestId,
                                    number,
                                    SupplyCenterPanel.this::refreshAllTables
                            );
                        } else {
                            JOptionPane.showMessageDialog(
                                    SupplyCenterPanel.this,
                                    "Заявка не найдена"
                            );
                        }

                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(
                                SupplyCenterPanel.this,
                                "Ошибка: " + ex.getMessage()
                        );
                    }
                }
            }
        });

        loadRequests(model, archived);

        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        return panel;
    }

    public void loadRequests(DefaultTableModel model, boolean archived) {

        model.setRowCount(0);

        try (Connection conn = DbManager.connect(Main.DB_NAME)) {

            String where = archived
                    ? "r.archived = 1"
                    : "r.archived = 0";

            String sql = """
                SELECT 
                    r.id,
                    r.number,
                    u.workshop_number,
                    r.status,
                    r.created_at,
                    COALESCE(COUNT(ri.id), 0) as item_count,
                    COALESCE(SUM(ri.quantity), 0) as total_qty
                FROM requests r
                JOIN users u ON u.username = r.created_by
                LEFT JOIN request_items ri ON ri.request_id = r.id
                WHERE %s
                GROUP BY r.id
                ORDER BY r.created_at DESC
            """.formatted(where);

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    model.addRow(new Object[]{
                            rs.getString("number"),
                            rs.getInt("workshop_number"),
                            rs.getString("status"),
                            rs.getString("created_at"),
                            rs.getInt("item_count"),
                            rs.getInt("total_qty")
                    });
                }
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Ошибка загрузки заявок:\n" + ex.getMessage()
            );
        }
    }

    private void refreshAllTables() {

        if (currentModel != null) {
            loadRequests(currentModel, false);
        }

        if (archiveModel != null) {
            loadRequests(archiveModel, true);
        }
    }

    private int getRequestIdByNumber(Connection conn, String number) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM requests WHERE number = ?")) {

            ps.setString(1, number);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        }
    }

    private void switchToAdminPanel() {
        mainFrame.getContentPane().removeAll();
        mainFrame.add(new AdminPanel(mainFrame, currentUser));
        mainFrame.revalidate();
        mainFrame.repaint();
    }
}
