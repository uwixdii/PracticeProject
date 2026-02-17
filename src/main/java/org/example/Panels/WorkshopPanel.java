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
import java.util.List;

public class WorkshopPanel extends JPanel {

    private final JFrame mainFrame;
    private final boolean isAdminMode;
    private final User currentUser;

    private final DefaultTableModel itemsModel = new DefaultTableModel(
            new String[]{"ID", "Наименование", "Остаток"}, 0);
    private final DefaultTableModel requestsModel = new DefaultTableModel(
            new String[]{"№ заявки", "Статус", "Дата", "Позиций", "Всего ед."}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    public WorkshopPanel(JFrame frame, boolean adminMode, User user) {
        this.mainFrame = frame;
        this.isAdminMode = adminMode;
        this.currentUser = user;

        setLayout(new BorderLayout(10, 10));

        JLabel title = new JLabel("Цех №" + user.getWorkshopNumber() + " — " + user.getUsername(), SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        add(title, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setResizeWeight(0.5);

        JPanel top = new JPanel(new BorderLayout());
        JTable itemsTable = new JTable(itemsModel);
        top.add(new JScrollPane(itemsTable), BorderLayout.CENTER);

        JButton createBtn = new JButton("Создать новую заявку");
        createBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        createBtn.addActionListener(e -> createNewRequest());
        top.add(createBtn, BorderLayout.SOUTH);

        split.setTopComponent(top);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(new JLabel("Мои заявки", SwingConstants.CENTER), BorderLayout.NORTH);

        JTable reqTable = new JTable(requestsModel);
        reqTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        reqTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String status = (String) table.getValueAt(row, 1);
                if ("approved".equals(status)) c.setBackground(new Color(200, 255, 200));
                else if ("rejected".equals(status)) c.setBackground(new Color(255, 200, 200));
                else c.setBackground(Color.WHITE);
                return c;
            }
        });

        reqTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = reqTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        String number = (String) requestsModel.getValueAt(row, 0);
                        try (Connection conn = DbManager.connect(Main.DB_NAME)) {
                            int requestId = getRequestIdByNumber(conn, number);
                            if (requestId >= 0) {
                                // ← Здесь правильный вызов с callback
                                new RequestDetailDialog(mainFrame, currentUser, requestId, number, WorkshopPanel.this::loadData);
                            } else {
                                JOptionPane.showMessageDialog(WorkshopPanel.this, "Заявка не найдена");
                            }
                        } catch (SQLException ex) {
                            JOptionPane.showMessageDialog(WorkshopPanel.this, "Ошибка открытия заявки:\n" + ex.getMessage());
                        }
                    }
                }
            }
        });

        bottom.add(new JScrollPane(reqTable), BorderLayout.CENTER);
        split.setBottomComponent(bottom);

        add(split, BorderLayout.CENTER);

        if (isAdminMode) {
            JButton back = new JButton("← Назад в админ-панель");
            back.setFont(new Font("SansSerif", Font.BOLD, 14));
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
        itemsModel.setRowCount(0);
        requestsModel.setRowCount(0);

        try (Connection conn = DbManager.connect(Main.DB_NAME)) {
            List<Item> items = DbManager.getAllItems(conn);
            for (Item i : items) {
                itemsModel.addRow(new Object[]{i.getId(), i.getName(), i.getQuantity()});
            }

            List<RequestSummary> reqs = DbManager.getMyRequests(conn, currentUser.getUsername());
            for (RequestSummary r : reqs) {
                requestsModel.addRow(new Object[]{
                        r.getNumber(),
                        r.getStatus(),
                        r.getCreatedAt(),
                        r.getItemCount(),
                        r.getTotalQuantity()
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Ошибка загрузки данных:\n" + ex.getMessage());
        }
    }

    private void createNewRequest() {
        JDialog dialog = new JDialog(mainFrame, "Создание заявки", true);
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(mainFrame);

        JPanel content = new JPanel(new BorderLayout(10, 10));

        DefaultTableModel model = new DefaultTableModel(
                new String[]{"Выбрать", "ID", "Наименование", "Количество", "Остаток"}, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return switch (columnIndex) {
                    case 0 -> Boolean.class;
                    case 1, 3, 4 -> Integer.class;
                    default -> String.class;
                };
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0 || column == 3;
            }
        };

        JTable table = new JTable(model);
        table.setRowHeight(28);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(1).setMaxWidth(60);
        table.getColumnModel().getColumn(3).setMaxWidth(100);
        table.getColumnModel().getColumn(4).setMaxWidth(100);

        content.add(new JScrollPane(table), BorderLayout.CENTER);

        try (Connection conn = DbManager.connect(Main.DB_NAME)) {
            List<Item> items = DbManager.getAllItems(conn);
            for (Item i : items) {
                model.addRow(new Object[]{false, i.getId(), i.getName(), 1, i.getQuantity()});
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(dialog, "Не удалось загрузить товары");
            dialog.dispose();
            return;
        }

        JButton saveBtn = new JButton("Сохранить заявку");
        saveBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        saveBtn.addActionListener(e -> {
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }

            System.out.println("=== Сохранение заявки ===");
            System.out.println("Строк в таблице: " + model.getRowCount());
        });

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
        south.add(saveBtn);
        content.add(south, BorderLayout.SOUTH);

        dialog.add(content);
        dialog.setVisible(true);
    }

    private int getRequestIdByNumber(Connection conn, String number) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM requests WHERE number = ?")) {
            ps.setString(1, number);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        }
    }

    private void showRequestDetails(int requestId, String number) {
        if (requestId < 0) {
            JOptionPane.showMessageDialog(this, "Заявка не найдена");
            return;
        }
        new RequestDetailDialog(mainFrame, currentUser, requestId, number, this::loadData);
    }
}