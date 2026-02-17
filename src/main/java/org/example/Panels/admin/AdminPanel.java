package org.example.Panels.admin;

import org.example.Item;
import org.example.Main;
import org.example.Panels.SupplyCenterPanel;
import org.example.Panels.WarehousePanel;
import org.example.Panels.WorkshopPanel;
import org.example.User;
import org.example.DbManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class AdminPanel extends JPanel {

    private final JFrame mainFrame;
    private final User currentUser;

    private final DefaultTableModel usersModel = new DefaultTableModel(
            new String[]{"ID", "Логин", "Роль", "Цех №"}, 0);
    private final DefaultTableModel itemsModel = new DefaultTableModel(
            new String[]{"ID", "Наименование", "Остаток"}, 0);

    public AdminPanel(JFrame frame, User user) {
        this.mainFrame = frame;
        this.currentUser = user;
        setLayout(new BorderLayout(10, 10));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Пользователи", createUsersPanel());
        tabs.addTab("Товары на складе", createItemsPanel());

        add(tabs, BorderLayout.CENTER);

        // Нижняя панель навигации
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 15));
        JButton btnWorkshop = new JButton("Цех");
        JButton btnWarehouse = new JButton("Склад");
        JButton btnSupply   = new JButton("Снабжение");
        JButton btnLogout   = new JButton("Выйти");

        nav.add(btnWorkshop);
        nav.add(btnWarehouse);
        nav.add(btnSupply);
        nav.add(Box.createHorizontalStrut(60));
        nav.add(btnLogout);

        add(nav, BorderLayout.SOUTH);

        // Обработчики переключения
        btnWorkshop.addActionListener(e -> switchPanel(new WorkshopPanel(mainFrame, true, currentUser)));
        btnWarehouse.addActionListener(e -> switchPanel(new WarehousePanel(mainFrame, true, currentUser)));
        btnSupply.addActionListener(e -> switchPanel(new SupplyCenterPanel(mainFrame, true, currentUser)));

        btnLogout.addActionListener(e -> {
            mainFrame.dispose();
            Main.createLoginWindow();
        });

        loadUsers();
        loadItems();
    }

    private JPanel createUsersPanel() {
        JPanel p = new JPanel(new BorderLayout());
        JTable tbl = new JTable(usersModel);
        tbl.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        p.add(new JScrollPane(tbl), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel();
        JButton addBtn = new JButton("Добавить");
        JButton delBtn = new JButton("Удалить");

        btnPanel.add(addBtn);
        btnPanel.add(delBtn);
        p.add(btnPanel, BorderLayout.SOUTH);

        addBtn.addActionListener(e -> JOptionPane.showMessageDialog(this, "Добавление пока не реализовано"));
        delBtn.addActionListener(e -> deleteSelectedUser(tbl));

        return p;
    }

    private void deleteSelectedUser(JTable tbl) {
        int row = tbl.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Выберите пользователя");
            return;
        }

        int id = (Integer) usersModel.getValueAt(row, 0);
        String name = (String) usersModel.getValueAt(row, 1);

        if (JOptionPane.showConfirmDialog(this,
                "Удалить " + name + " ?",
                "Подтверждение",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;

        try (Connection conn = DbManager.connect(Main.DB_NAME)) {
            DbManager.deleteUser(conn, id);
            loadUsers();
            JOptionPane.showMessageDialog(this, "Пользователь удалён");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Ошибка:\n" + ex.getMessage());
        }
    }

    private JPanel createItemsPanel() {
        JPanel p = new JPanel(new BorderLayout());
        JTable tbl = new JTable(itemsModel);
        p.add(new JScrollPane(tbl), BorderLayout.CENTER);

        JButton addBtn = new JButton("Добавить товар");
        addBtn.addActionListener(e -> addNewItem());
        p.add(addBtn, BorderLayout.SOUTH);

        return p;
    }

    private void addNewItem() {
        String name = JOptionPane.showInputDialog(this, "Название товара:");
        if (name == null || name.trim().isEmpty()) return;

        String qtyStr = JOptionPane.showInputDialog(this, "Начальное количество:");
        int qty;
        try {
            qty = Integer.parseInt(qtyStr);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Количество должно быть числом");
            return;
        }

        try (Connection conn = DbManager.connect(Main.DB_NAME)) {
            DbManager.addItem(conn, name.trim(), qty);
            loadItems();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Ошибка добавления:\n" + ex.getMessage());
        }
    }

    private void loadUsers() {
        usersModel.setRowCount(0);
        try (Connection c = DbManager.connect(Main.DB_NAME)) {
            List<User> list = DbManager.getAllUsers(c);
            for (User u : list) {
                usersModel.addRow(new Object[]{
                        u.getId(),
                        u.getUsername(),
                        u.getRole(),
                        u.getWorkshopNumber() > 0 ? u.getWorkshopNumber() : ""
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Ошибка загрузки пользователей");
        }
    }

    private void loadItems() {
        itemsModel.setRowCount(0);
        try (Connection c = DbManager.connect(Main.DB_NAME)) {
            List<Item> list = DbManager.getAllItems(c);
            for (Item i : list) {
                itemsModel.addRow(new Object[]{i.getId(), i.getName(), i.getQuantity()});
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Ошибка загрузки товаров");
        }
    }

    public void switchPanel(JPanel newPanel) {
        mainFrame.getContentPane().removeAll();
        mainFrame.add(newPanel);
        mainFrame.revalidate();
        mainFrame.repaint();
    }
}